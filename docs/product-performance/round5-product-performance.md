# Round5 상품 조회 성능 개선

## 선택 요약

- 좋아요 수 정렬은 별도 Materialized View 테이블 대신 기존 `products.like_count` 비정규화 컬럼을 사용한다.
- MySQL은 네이티브 Materialized View를 지원하지 않으므로, 별도 MV는 요약 테이블 + refresh 로직을 직접 운영해야 한다.
- 이번 과제는 상품 목록의 브랜드 필터 + 좋아요순 정렬을 빠르게 만드는 것이 핵심이므로, 읽기 경로가 단순하고 인덱스 효과가 큰 `like_count` 컬럼 방식을 선택한다.

## AS-IS

- `GET /api/v1/products`는 전체 활성 상품을 `List`로 조회한 뒤 응답했다.
- 대량 데이터에서 page/size 제한이 없어 응답 크기와 DB 조회량이 함께 커진다.
- 좋아요순 조회는 `WHERE deleted_at IS NULL ORDER BY like_count DESC, id DESC` 또는 `WHERE brand_id = ? AND deleted_at IS NULL ORDER BY like_count DESC, id DESC` 형태인데, 정렬에 맞춘 복합 인덱스가 없으면 filesort와 큰 rows scan이 발생할 수 있다.
- 상품 상세/목록은 매 요청마다 DB를 조회했다.

## TO-BE

- 목록 API는 `page`, `size`를 받아 `Page` 단위로 조회한다.
  - 기본값: `page=0`, `size=20`
  - 검증: `page >= 0`, `1 <= size <= 100`
- 좋아요순 정렬 최적화를 위해 `products`에 복합 인덱스를 추가한다.
  - 브랜드 필터 있음: `idx_products_brand_deleted_likes_id (brand_id, deleted_at, like_count, id)`
  - 브랜드 필터 없음: `idx_products_deleted_likes_id (deleted_at, like_count, id)`
- 좋아요 등록/취소 시 count 동기화는 원자적 SQL UPDATE를 유지한다.
  - 증가: `like_count = like_count + 1`
  - 감소: `like_count = like_count - 1 WHERE like_count > 0`
- Redis read-through cache를 적용한다.
  - 상세 key: `commerce-api:product:detail:v1:{productId}`, TTL 60초
  - 목록 key: `commerce-api:product:list:v1:brand:{brandId|all}:sort:{sort}:page:{page}:size:{size}`, TTL 30초
  - 좋아요 count 이벤트 처리 후 상세 캐시만 삭제한다. 목록 캐시는 짧은 TTL로 최신성 지연을 제한한다.

## 더미 데이터 특성

- 총 상품 수는 100,000건이다.
- 브랜드는 20개이며, 각 브랜드에 5,000건씩 균등하게 분포한다.
- `like_count`는 `(n * 17) % 10000` 수식으로 생성해 0~9,999 범위에 분포한다.
- 삭제 상품은 `n % 25 = 0` 조건으로 생성하며, 전체의 약 4%가 `deleted_at != NULL` 상태다.
- 실제 운영 데이터는 인기 상품 쏠림, 브랜드별 상품 수 불균형, 삭제 비율 차이가 있을 수 있으므로 EXPLAIN 결과를 해석할 때 데이터 분포 차이를 함께 고려해야 한다.

## EXPLAIN 측정 절차

1. `docker-compose -f ./docker/infra-compose.yml up -d`로 MySQL/Redis를 띄운다.
2. 로컬 프로필에서 애플리케이션을 한 번 실행해 Hibernate가 스키마를 생성하게 한다.
3. `docs/product-performance/round5-product-performance.sql`을 실행해 20개 브랜드와 100,000개 상품을 준비한다.
4. SQL 파일의 측정 블록을 순서대로 선택 실행하며, 인덱스 없음, 후보 인덱스, 최종 인덱스 상태에서 `EXPLAIN ANALYZE`를 비교한다.

| Query | Index | type/key | rows | Extra | actual time |
| --- | --- | --- | ---: | --- | ---: |
| brand + likes desc | none | 실행 후 기록 | 실행 후 기록 | 실행 후 기록 | 실행 후 기록 |
| brand + likes desc | `(brand_id, like_count, id)` | 실행 후 기록 | 실행 후 기록 | 실행 후 기록 | 실행 후 기록 |
| brand + likes desc | `(brand_id, deleted_at, like_count, id)` | 실행 후 기록 | 실행 후 기록 | 실행 후 기록 | 실행 후 기록 |
| all + likes desc | none | 실행 후 기록 | 실행 후 기록 | 실행 후 기록 | 실행 후 기록 |
| all + likes desc | `(deleted_at, like_count, id)` | 실행 후 기록 | 실행 후 기록 | 실행 후 기록 | 실행 후 기록 |

좋은 신호는 `key`가 의도한 인덱스로 잡히고, `Using filesort`가 사라지며, `rows`와 actual time이 감소하는 것이다.
