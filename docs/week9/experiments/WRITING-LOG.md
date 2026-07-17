# Round 9 Writing Log — 실시간 상품 랭킹 (Redis Sorted Set)

> 글쓰기 퀘스트(테크노트/블로그)의 **결정 근거 축적** 파일. "무엇을 했다"가 아니라 **"왜 그렇게 판단했나"** 를 결정 시점마다 한 덩어리씩 쌓는다.
> 요구사항: `../00-requirements.html` · 체크리스트: `../checklist.md`
> 후보 주제: 롱테일/시간 양자화 · 콜드 스타트 · 랭킹 지표 구성 근거 · ZSET 메모리(상위 N) · Top-N 캐싱 트레이드오프.

---

## 결정 대기 (진입 전 확정할 것)

- [x] **스코어 모델** — view 0.1 / like 0.2 / **주문 order line당 0.7 고정 (가격·수량 미반영)**. 2026-07-15 결정 로그 참조.
- [x] **감소 이벤트** — 좋아요 취소 **대칭 차감(−0.2)**, 음수 score 허용. 2026-07-15 결정 로그 참조.
- [ ] **ZSET 갱신 위치** — collector 소비 트랜잭션/배치 안(정합성) vs 분리(결합도↓). product_metrics upsert와의 관계.
- [ ] **TTL 부여 방식** — 매 이벤트 expire 재설정 회피(조건부/Lua).
- [ ] **Aggregation** — ZSET → 상품 정보 조합 시 N+1 회피(IN 일괄 / 캐시).
- [ ] **API 응답 계약** — 빈 랭킹/미진입 rank=null 형태, 페이지네이션 경계.
- [ ] **유실·복구 전략** — Redis = 점수의 유일 저장소(캐시 아님). 유실 시 Kafka 재소비는 EventHandled 멱등에 막힘 → 멱등 경계 분리 vs 감수 vs persistence. TTL = 이력 포기 결정이기도 함.
- [ ] **carry-over 가중치 = 시스템의 기억력 파라미터** — 매일 ×0.1 연쇄 = 지수 감쇠(이틀 전 0.01배·사흘 전 0.001배, 과거 총 기여 상한 ≈ ×0.111). 과거 head 고착(롱테일)이 구조적으로 불가능해짐. 0.5=안정 우선 / 0.1=신선도 우선 / 0.01=사실상 리셋 — 콜드 스타트 땜빵이 아니라 신선도 vs 안정성 다이얼 (2026-07-14 논의).
- [x] **carry-over 실행 시각 (글감: "왜 23:50인가")** — 23:50 사전 생성: 23:50~00:00 10분 시딩 누락(실효 ~0.07%) 감수, ZUNIONSTORE 덮어쓰기라 재실행 멱등 + 새 판 쓰기와 경합 없음. 대안 00:00 self-union(`ZUNIONSTORE 오늘 2 오늘 어제 WEIGHTS 1 0.1`): 누락 0·원자적이지만 재실행 시 중복 가산(비멱등). 시딩은 근사치가 목적 → 멱등 우선으로 23:50 채택 (2026-07-14 논의). **→ 2026-07-15 dual write 채택으로 대체** (결정 로그 참조 — 배치 자체가 사라져 실행 시각 논점 소멸, 단 이 비교는 "버린 대안" 글감으로 유효).

---

## 결정 로그

## 2026-07-15 — Stage 1b 읽기 인덱스: tie-break 가 filesort 를 만들고, COUNT 는 옵티마이저가 배신한다 (EXPLAIN 실측)

### 결정 — 인덱스를 `(ranking_date, score DESC, product_id ASC)` 3컬럼으로 확장. 개별 순위 COUNT 의 옵티마이저 미스는 Stage 1b 한계 데이터로 보존

- 배경/문제: Stage 1b 구현의 읽기 쿼리 2종을 exp01 테이블(10만 행)로 EXPLAIN 검증. ① 페이지 = `WHERE ranking_date=? ORDER BY score DESC, product_id ASC LIMIT 20` ② 개별 순위 = 내 score 조회(uk const, 문제 없음) 후 `COUNT(*) WHERE ranking_date=? AND score > ?`.
- 발견 1 (페이지): 2컬럼 인덱스 `(ranking_date, score DESC)`에서 **tie-break `product_id ASC`가 인덱스 정렬 순서를 깨** filesort 로 5만 행 전체 정렬 (tie-break 를 빼면 인덱스 사용). → **3컬럼 인덱스로 ORDER BY 를 인덱스 순서와 일치**시키면 `Using index`(커버링) + filesort 소멸 + 페이지네이션 결정성 유지.
- 발견 2 (COUNT): 3컬럼 커버링 인덱스가 있어도 **옵티마이저가 uk(비커버링, 행 룩업 10만 회)를 선택**. EXPLAIN ANALYZE 실측 — uk 44.8ms vs FORCE INDEX 커버링 range 14.5ms (**3배**). 옵티마이저 cost 추정(1977 vs 10153)이 실제와 반대. exp02 에서 본 "개별 순위 = 최악 경로(29qps)"의 원인 그대로.
- 고른 것 & 왜: 인덱스는 3컬럼으로 확장(페이지 경로는 완전 해결). COUNT 는 **FORCE INDEX 를 넣지 않고 이식성 있는 파생 쿼리 유지** — H2 테스트 호환 리스크를 지면서까지 고칠 필요가 없는 게, 이 비용이 바로 로드맵이 실측으로 보이려는 "Stage 1 의 개별 순위 한계"이고 Stage 3 ZSET `ZREVRANK`(O(log N))의 존재 이유다. 상세 조회당 15~45ms 급 순위 쿼리는 한계 데이터포인트로 보존.
- 트레이드오프: 3컬럼 인덱스는 엔트리가 커져 쓰기 시 재배치 비용 소폭 증가 (Stage 1b 쓰기 실측 항목에 포함됨). FORCE INDEX 미사용으로 상세 순위는 최적 경로 대비 3배 비쌈 — 운영 전환 시 힌트 도입 여지 기록.

## 2026-07-15 — 주문 스코어는 금액이 아니라 "결정 횟수"를 센다 (스코어 모델 확정)

### 결정 — view +0.1 / like ±0.2 (대칭 차감) / 주문 order line당 +0.7 고정. 가격·수량은 랭킹 점수에 넣지 않는다 (사용자 확정)

- 배경/문제: 요구사항 예시는 주문 Score = `price×amount` (정규화 시 log). 원시값이면 30만원×2 = 360,000점으로 view 0.1을 압살 → log 정규화를 검토하다 사용자가 근본 질문 제기: "애초에 가격이 인기 랭킹에 반영되는 게 맞나?"
- 조사 (공식 출처 13곳, `../purchase-signal-research.html`): **"인기" 랭킹에서 구매를 금액으로 반영하는 공증 사례 없음.** 치환 방식은 4분류로 수렴 — ① 건수 카운트(Shopify "number of orders that include the product" · Amazon BSR) ② 매출 합산(Steam Top Sellers — 스스로 "판매 랭킹"이라 명명, 24h 롤링+3h 부스트) ③ 압축·지수화(Elastic log1p · Reddit log10 · Algolia tie-break) ④ ML feature(Etsy·Airbnb·Instacart — 전환 확률 예측). 시간 감쇠(recency weighting)는 전 사례 공통 → 우리 일간 버킷+carry 0.1과 방향 일치.
- 고른 것 & 왜: **① 건수 카운트** — 인기의 신호는 "구매 결정이 일어났다"는 사실이지 상품의 가격대가 아님(가격 반영 = 고가 카테고리 구조 편향). 수량도 버림(Shopify와 동일) — 저가 100개 사재기가 선형 부스트되는 구멍 차단. 수량·금액 정보는 `product_metrics`와 주문 데이터에 이미 보존되므로 정보 손실 없음. weight는 허용 범위(0.6~0.7)에서 **0.7** — 검증 요건 "주문 1건 > 좋아요 3건"(0.7 > 0.6) 충족.
- 감소 이벤트: **대칭 차감(−0.2)** — `product_metrics.like_count` 차감과 동작 일치, like↔unlike 반복 어뷰징이 점수로 안 남음, 음수 허용은 Redis ZINCRBY 의미론과 동일해 Stage 3 전환 시 동작 보존.
- 버린 대안 & 왜: (b) `0.7×quantity` — 판매량 랭킹 의미론 + 직접 대응 사례 없음 + 대량 주문 선형 부스트. (c) `0.6×ln(1+price×qty)` — 요구사항 예시형이지만 log 압축 후 가격 100배 차이가 점수 1.5배로 줄어 사실상 상수 + 미세 보정(유지 실익 작음), "인기에 왜 가격이" 의미론 문제 잔존. log 정규화 자체(Elastic·Reddit)는 "금액을 굳이 넣을 때"의 안전장치일 뿐 넣을 이유를 만들어주지 않음.
- 트레이드오프 / 남은 리스크: ① 요구사항 예시에서 의도적으로 이탈 — 요구사항 스스로 "예시, 우리 기준 재산정"을 요구하므로 근거 기록으로 충족 ② 고가 1건과 저가 1건이 동점 — 의도된 결과("인기"의 정의) ③ 매출 기여를 보고 싶으면 별도 "판매 랭킹"(Steam 계열)을 만드는 게 정직한 해법 (블로그 소재: "가격을 반영하는 게 맞냐는 질문에 업계는 이미 답해놨다").

## 2026-07-15 — carry-over는 23:50 배치가 아니라 dual write로 (집계는 일간 버킷 유지)

### 결정 — 이벤트 소비 시 오늘 판 `+Δ`와 내일 판 `+Δ×0.1`을 함께 쓴다 (사용자 결정)

- 배경/문제: 23:50 사전 시딩 배치는 ① 배치 실패 시 carry 미작동(감지·재실행 운영 부품 필요 — lazy 생성 덕에 "터지진" 않지만 콜드 스타트로 조용히 퇴화) ② 23:50~00:00 10분이 carry 기준에서 누락(자정 직전 경향성). 시딩을 자정 후로 미루면 콜드 스타트 노출 + 이미 생긴 행과의 병합이 비멱등.
- 고른 것 & 왜: **dual write** — carry가 본 쓰기 경로의 멱등(`EventHandled`)·트랜잭션 보장을 그대로 상속해 **배치라는 실패 모드가 클래스째 사라짐** + 누락 창 0. 내일 판 쓰기는 다른 날짜 프리픽스 append라 EXP-01 결론(읽는 판 보호)과 충돌 없음. 채택의 주 근거는 "10분 경향성"(정량으로는 ~0.07%로 미미)이 아니라 **carry 신뢰성의 승격 + 배치 운영 제거**. 추가 근거(사용자): 배치 계열의 안전장치인 "통짜 덮어쓰기(DELETE+INSERT 재시딩)"·제자리 UPDATE는 **덮어쓰기 = 데이터 상실 연산** — 누적(additive)만 존재하는 쓰기 경로가 복구·감사 관점에서 구조적으로 안전하다.
- 버린 대안 & 왜: 23:50 배치 시딩(핫패스 경량·멱등이지만 배치 운영 부품 + 10분 누락) / 00:00 self-union·자정 후 upsert 시딩(재실행 중복 가산 비멱등 + 콜드 스타트 노출). **시간(1h) 버킷·슬라이딩 윈도우**(자정 절벽의 근본 해법)는 요구사항이 "일간만"이라 보류 — 절벽은 일간 랭킹의 정의로 수용.
- 트레이드오프 / 남은 리스크: ① 이벤트당 쓰기 2배 — 핫패스 영구 비용 (**Stage 1b에서 단일 vs dual 처리량·p95 실측 예정**) ② 감쇠가 1일 절단(배치는 어제 carry 포함 score×0.1이라 지수 연쇄가 살지만, 델타 dual write는 ×0.01 항 소실 — 무시 가능 수준이나 "지수 감쇠" 프레이밍은 수정) ③ 실시간 가중치 변경(Nice) 시 내일 판이 구·신 가중치 혼합 ④ 트랜잭션이 2행을 만지므로 잠금 순서(날짜 오름차순) 고정 필요.
- 부수 논의: 자정 절벽 자체의 정량화 — 23:59 이벤트는 2분 뒤 ×0.1, 하루 ×0.1 = 반감기 ~7.2h 기준이면 10분 이벤트의 "이상적" 가중치는 ~0.98. 반대로 하루 안에서는 14시간 전 이벤트도 ×1.0. 즉 일간 버킷은 방향 왜곡이 아니라 양자화 — 트렌딩 요구가 생기면 답은 가중치 조정이 아니라 버킷 세분화 (블로그 소재: "0.1을 의심했는데 범인은 버킷이었다").

## 2026-07-14 — EXP-01 실측: 날짜 경계는 "제자리 감쇠"가 아니라 "사전 시딩"이다

### 결정 — 자정 배치는 오늘 판을 UPDATE하지 않고, 내일 판을 미리 INSERT한다 (가설 실측 채택)

- 배경/문제: RDB 번역 설계에서 날짜 경계 처리 두 안 — A) 제자리 전면 `UPDATE score×0.1` vs B) 사전 `INSERT INTO 내일 SELECT ×0.1`. "배치가 도는 동안 조회가 얼마나 아픈가"가 쟁점.
- 실측 (10만 행 · conc32): A = 조회 처리량 **−41%** · 배치 24.1s · 꼬리지연 max 1.5배 / B = **−10%** · 배치 3.9s · 꼬리지연 baseline 수준. 상세: `exp01/README.md`.
- 왜: A는 score 변경이 `(date, score DESC)` 인덱스 10만 엔트리의 삭제+재삽입이 됨(조회가 읽는 그 페이지들). B는 다른 날짜 프리픽스에 append — 오늘 판을 안 건드림. MVCC라 블로킹은 없지만 자원 경합으로 열화.
- 함의: ① ZSET carry-over(23:50 사전 생성)의 구조적 정당성이 RDB에서도 재현됨 — "쓰는 판과 읽는 판을 분리하라" ② "SELECT는 잠금에 안 막힌다 ≠ 영향 없다"를 수치로 확보 (블로그 소재).

## 2026-07-14 — 단계적 구현·측정 전략 채택 (MySQL → 캐시 → ZSET)

### 결정 — 바로 ZSET으로 가지 않고 3단계로 업그레이드하며 각 단계를 같은 조건에서 측정한다

- 배경/문제: "왜 ZSET인가"의 근거가 요구사항 문서의 서술("RDB는 느려진다")뿐이면 글쓰기 퀘스트에서 주장이 된다. 실측 수치가 있어야 결정이 된다.
- 고른 것 & 왜: Stage 1 MySQL 쿼리(방식 A) → Stage 2 Redis TTL 캐시(방식 D 계열) → Stage 3 ZSET(방식 B). 같은 시드·같은 k6 시나리오·같은 지표로 3자 비교 → 탈락 사유가 요구사항 항목(실시간 반영·개별 순위·조회 빈도)에 1:1로 매핑됨. R8 부하테스트 자산 재사용으로 측정 비용 최소화.
- 버린 대안 & 왜: 바로 ZSET 구현(빠르지만 비교 근거 없음 — 블로그가 "썼다" 글이 됨) / 4방식 전부 구현(C 스트림 처리기는 인프라 신설 필요 — 과잉).
- 트레이드오프 / 남은 리스크: Stage 1·2에 시간 소모 (완화: 각 2h·1.5h 타임박스, 제출물 아닌 실험으로 취급). product_metrics에 날짜 차원이 없어 Stage 1은 누적 랭킹 — 오히려 "시간 양자화 필요성"을 실측으로 발견하는 장치로 활용.

<!-- 결정할 때마다 아래 형식으로 추가:
## YYYY-MM-DD — <결정 제목>
### 결정 — <한 줄 결론>
- 배경/문제:
- 고른 것 & 왜:
- 버린 대안 & 왜:
- 트레이드오프 / 남은 리스크:
-->

## 2026-07-16 — 이벤트 소비를 타입 계약으로 전환 + 토픽 rename

### 결정 — JsonNode 손파싱·String eventType 분기를 걷어내고, 토픽별 리스너 + Jackson 다형 역직렬화로 전환한다

- 배경/문제: 컨슈머가 `ConsumerRecord<Any, Any>`(실제 와이어 타입은 key String·value ByteArray)로 받고 `readTree` 손파싱 → 서비스가 String eventType 분기 + `payload["productId"]` 하드코딩. 필드명 오타·처리 누락을 컴파일러가 못 잡고, `as ByteArray` 무근거 캐스트 실패조차 catch(Exception)이 "파싱 불가 skip"으로 위장. 리스너 1개가 토픽 3개를 한 파이프로 소비해 view 폭주가 order 처리를 막는 head-of-line blocking도 존재.
- 고른 것 & 왜: ① 토픽별 `@KafkaListener` 3개(그룹 분리 — 격리·토픽별 랙 관측·리밸런스 독립) ② 스트리머 소유 소비 계약(`ConsumedEvents.kt`) — `@JsonTypeInfo(property="eventType")` sealed(ProductEvent) + 단일 타입 토픽은 data class 직행 ③ 서비스는 타입 오버로드 `handle(event, occurredAt)` — sealed `when` 망라로 처리 누락이 컴파일 에러. 프로듀서가 `spring.json.add.type.headers=false`라 헤더 기반 공식 옵션(TYPE_MAPPINGS·DelegatingDeserializer)이 닫혀 있어 본문 discriminator가 정석 경로 (Spring Kafka serdes 문서 + Jackson 공식 다형 역직렬화 문서로 확인).
- 버린 대안 & 왜: deserializer 단 타입화(`ErrorHandlingDeserializer` + `VALUE_DEFAULT_TYPE`) — 기능 동등하나 앱 전용 consumer factory 설정 신설 필요(베이스 modules/kafka 수정 금지) + 실패 처리(warn+skip)가 설정·헤더 검사로 흩어짐. / 프로젝션별(metrics·ranking) 컨슈머 그룹 분리 — 이번 요지(이벤트 종류별 분리)와 다른 축, Stage 3에서 랭킹이 Redis로 가며 트랜잭션 원자성이 깨질 때 재결정.
- 함정 발견: Kotlin data class의 프리미티브 필수 필드(Long)는 JSON 누락 시 예외가 아니라 0으로 채워짐 — 테스트가 잡아냄. 컨슈머 전용 mapper에 `FAIL_ON_MISSING_CREATOR_PROPERTIES`·`FAIL_ON_NULL_FOR_PRIMITIVES`를 켜서 계약 위반이 조용히 통과하지 않게 강제.
- 부수 결정: `catalog-events` → `product-events` rename — 코드 전체가 product 어휘(패키지·이벤트명·aggregateType "PRODUCT")인데 토픽만 catalog여서 유추 불가. week7에 직접 지은 이름이라 계약 파트너 조율 불요, 브로커 auto-create로 마이그레이션 무비용(테스트 데이터 환경).
- 트레이드오프 / 남은 리스크: ① 컨슈머 스레드 3→9 ② 그룹 rename으로 기존 오프셋 승계 없음(latest 시작 — 측정 환경이라 무해) ③ 프로듀서·컨슈머 스키마 이중 정의는 앱 간 계약의 정상 비용.

## 2026-07-16 — 구독(EventSubscription)별 컨슈머 분리 — metrics 와 ranking 의 독립 소비

### 결정 — 한 컨슈머가 두 프로젝션을 한 트랜잭션으로 처리하던 구조를, 구독별 컨슈머 그룹 + 구독별 멱등 기록으로 분리한다

- 배경/문제: `ProductMetricsService.handle()` 하나가 metrics 카운터와 랭킹 dual write 를 같은 트랜잭션에서 처리 — ① 두 책임이 운명공동체(랭킹 지연 = metrics 랙) ② `event_handled` 가 컨슈머 공용이라 랭킹만 재처리(replay) 불가능 ③ Stage 3 에서 랭킹이 Redis 로 가면 어차피 한 트랜잭션이 성립 불가. 결정적으로 두 구독이 원하는 기록이 다름 — metrics 는 종류별 카운터, ranking 은 균일한 점수 델타.
- 고른 것 & 왜: ① `ProductRankingKafkaConsumer` 신설(그룹 `commerce-streamer-ranking-{topic}`) — 경계에서 이벤트를 `ScoreChange(productId, amount)` 로 변환해 `RankingAccumulateService.accumulate()` 에 위임 (서비스 입력 = 그 구독이 원하는 기록 그 자체) ② `event_handled` 를 `(event_id, subscription)` 복합키로 — 구독별 처리 기록이라 "이 이벤트가 각 프로젝션에 반영됐는가"를 구독별로 검증 가능 ③ 구분자는 String 이 아니라 `EventSubscription { METRICS, RANKING }` enum(@Enumerated STRING). bare `Subscription` 은 커머스 도메인의 구독 결제와 충돌해 도메인 자격어를 붙임. Pub/Sub 의 topic→subscription 모델과 동일한 어휘.
- 버린 대안 & 왜: 단일 컨슈머 유지(metrics-ranking 원자성) — 그 원자성을 사용하는 소비자가 없음(정산 검증도 k6 카운터 대 저장소 대사). / handler 문자열 상수 — 방금 String eventType 분기를 걷어낸 것과 같은 이유로 기각.
- 함정: 분리하는 순간 복합키 전환은 선택이 아니라 필수 — 단일 PK(event_id)면 둘째 구독의 insert 가 PK 충돌 → 롤백 → 재전달 무한루프.
- 부수 정리: 아키텍처 훅이 domain 레이어의 JPA import 위반을 검출(기존 EventHandled 가 domain 에 @Entity 로 존재) → 엔티티를 `metrics/infrastructure` 로 이동, domain 엔 enum + 순수 인터페이스(`exists`/`markHandled`)만. 소비 계약(`ConsumedEvents`)은 두 구독이 공유하므로 `shared/event` 로 이동. 역직렬화는 `ConsumedEventDeserializer` 오브젝트로 단일화.
- 트레이드오프 / 남은 리스크: ① 브로커 읽기 2배·이벤트당 트랜잭션 1→2 ② metrics-ranking 간 원자성 소멸(사용처 없음 확인) ③ Stage 3 에서 RANKING 구독의 멱등 기록을 MySQL 에 둘지 Redis(Lua dedup+ZINCRBY)로 옮길지는 열린 결정.

## 2026-07-16 — carry-over dual write 유지 + 하이브리드(배치 마커) 설계는 측정 후 fallback 으로

### 결정 — 무조건 dual write(오늘판 +Δ · 내일판 +0.1Δ)를 그대로 두고, EXP-03 측정에서 병목으로 나올 때만 하이브리드로 전환한다

- 배경/문제: dual write 는 이벤트당 upsert 2회 — "매번 두 번 쓰는 비용" 문제 제기. 대안으로 "특정 시점까지 단일 쓰기 → 배치로 내일판 일괄 생성(×0.1) → 그 이후만 dual write" 하이브리드를 검토.
- 검토한 하이브리드의 구멍과 보정: ① 최초 제안(마지막 저장 metric id 를 워터마크로 배치 끝점/dual write 시작점 분리)은 auto-increment id 가 커밋 순서와 일치하지 않아(갭·교차 커밋) 경계 유실 가능 → 기각. ② 보정판: 배치가 `INSERT INTO 내일판 SELECT ×0.1` + 완료 마커 insert 를 한 트랜잭션으로 원자화, 컨슈머는 eventDate+1 마커 존재 여부로 단일/dual 을 스위치, 배치 실행 순간의 race 는 RANKING 구독만 ~수 초 pause 로 직렬화(구독 분리가 이걸 가능하게 함).
- 고른 것 & 왜: 현행 유지. 하이브리드는 쓰기 2배가 실측 병목일 때만 가치가 있는데 아직 측정 전 — EXP-03 이 단일 upsert vs dual write 대조 런으로 그 비용을 %로 낸다. 지금 전환하면 배치 재도입(EXP-01 에서 -41% 근거로 버린 축) + 마커 상태머신 + pause 오케스트레이션이라는 운영 부품 3개를 근거 없이 사는 것.
- 트레이드오프 / 남은 리스크: EXP-03 에서 dual write 추가 비용이 소비 천장을 유의미하게 깎으면(랙 발산 유입률 하락) 이 하이브리드가 1순위 카드 — 설계는 이 엔트리에 보존됨.
- 추가 이점 발견 (2026-07-16 재논의): 배치 carry(어제판 전체 ×0.1)는 어제판에 포함된 carry 까지 이월하므로 지수 감쇠 연쇄(0.1→0.01→…)가 보존된다 — dual write 의 1일 절단(×0.01 항 소실)보다 수학적으로 원래 의도에 가까움. 전환 시 성능 외에 정확성도 하나 얻는다. 반대급부로 재확인된 결정 축: 00:00 증분 배치 변형은 콜드 스타트 창(자정~배치 완료, 부분 적용 노출)이 구조적으로 불가피 + 읽는 판(오늘판)에 10만 행 upsert 라 EXP-01 의 −41% 축에 가까움(자정 직후 빈 인덱스라 실측 미지수).

## 2026-07-16 — AWS 세팅이 잡아낸 실버그: 랭킹 읽기 API 의 LocalDate 가 실 MySQL 에서 -1일로 바인딩

### 결정 — JVM 기동 플래그 `-Duser.timezone=Asia/Seoul` 로 해소 (진범 = @PostConstruct 의 늦은 setDefault). 코드 레벨 근본 수정은 측정 후 별도 커밋

- 배경/문제: AWS 본런 세팅 중 `GET /rankings?date=20260716` 이 빈 배열 반환. general_log 실측 — 와이어에 `ranking_date='2026-07-15'` 로 바인딩. 원인 조합: ① 베이스 템플릿이 JVM TZ 를 KST 로 강제(`CommerceApiApplication` 의 `TimeZone.setDefault`) ② MySQL 서버(컨테이너) TZ = UTC ③ Connector/J 가 `java.sql.Date` 를 커넥션(=서버) TZ 로 포맷 → `2026-07-16 00:00 KST = 07-15 15:00 UTC` → DATE '2026-07-15'. 읽기(JPQL 파생 쿼리)와 쓰기(native upsert) 모두 동일하게 밀림.
- 왜 지금까지 안 잡혔나: ① H2 통합 테스트 — 같은 JVM 안에서 쓰기·읽기가 같은 변환을 받아 자기일관(테스트는 green) ② 로컬 스모크 — ranking-read.js 가 status 200 만 체크해 **빈 items 를 성공으로 오독** (이번에 k6 체크에 "items 비어있지 않음" 추가 필요 교훈) ③ R7~R8 은 DATE 컬럼을 안 씀.
- 시도해서 안 된 것 (전부 general_log 로 판정): `hibernate.jdbc.time_zone=Asia/Seoul` (SPRING_APPLICATION_JSON 로 정상 주입 확인) → 무효. `hibernate.timezone.default_storage=NORMALIZE` → 무효. 둘 다 LocalDate/DATE 바인딩에는 관여하지 않음 — 레버는 드라이버·서버 TZ.
- 진범 규명 (Hibernate bind TRACE 실측): Hibernate 는 `binding parameter (1:DATE) <- [2026-07-16]` — **정확히 바인딩**. 변환은 Connector/J 에서 발생. 핵심은 앱이 JVM TZ 를 main 이 아니라 **`@PostConstruct` 에서 setDefault** 한다는 것 — Hikari 풀·드라이버가 그보다 먼저 초기화되며 그 시점의 JVM TZ(EC2=UTC)를 캐시한다. 이후 요청 시점의 `java.sql.Date` 는 KST 자정 기준으로 만들어져 드라이버 캐시(UTC)로 포맷 → -1일. 방증: 외부 logback 의 `%d` 가 UTC 로 찍힘(로그백도 setDefault 이전 초기화) vs 응답 timestamp 는 +09:00(요청 시점 평가).
- 고른 것 & 왜: 기동 플래그 `-Duser.timezone=Asia/Seoul` — JVM 이 처음부터 KST 라 초기화 순서 레이스가 사라짐. 측정 커밋(6744aca) 불변. mysql 서버 TZ(+09:00 고정)는 CURDATE()·정산 SQL 의 비즈니스 날짜 정렬용으로 유지 (이것만으로는 미해결임을 실측 확인).
- 배제한 가설 (전부 실측): hibernate.jdbc.time_zone / timezone.default_storage 오버라이드(SPRING_APPLICATION_JSON·CLI 브래킷·additional-location yaml 3방식 모두) → 와이어 불변. 서버 TZ KST 단독 → 불변. 통계·인덱스·락 무관.
- 부수 발견: logback.xml 에 perf 프로파일 블록이 없어 **perf 부팅은 로그가 통째로 버려짐**(appender 미배선, 배너만 출력) — R8 때도 앱 로그가 없었던 이유. 측정 중 문제 추적을 위해 외부 logback(`--logging.config`) 자산 추가 고려.
- 남은 일 (측정 후): ① 코드 레벨 근본 수정 — `@PostConstruct` setDefault 를 main 첫 줄(runApplication 이전)로 옮기거나 JVM TZ 의존 제거, RDS(UTC) 대비 jdbc-url `connectionTimeZone` 명시 검토 ② 로컬 dev compose 도 동일 이슈 잠복 — 정렬 필요 ③ Testcontainers MySQL 재현 테스트 + k6 체크에 "items 비어있지 않음" 추가 ④ logback perf 프로파일 블록.

## 2026-07-17 — EXP-POOL: 읽기 천장은 커넥션 풀이 아니라 MySQL CPU (풀 10/40/80 실측)

### 결정 — "풀 40/40 소진과 함께 온 읽기 천장" 가설을 풀 3단계로 검증. 풀=증상, MySQL CPU=원인, 락=무관으로 확정. 문서 = `docs/week9/06-pool-sizing-diagnosis.html`

- 배경/문제: 7/16 본런 S-READ 천장 ~280rps·p95 3.19s가 Hikari 40/40 소진과 함께 왔다 → "풀 늘리면 천장 오르나" 가설. 단, 당시 앱 CPU 10%·p95≈connection-timeout(3s)·순수 인덱스 읽기라 정황은 반대(풀=증상)를 가리켰고, 7/16 런은 MySQL 측 지표를 안 남겨 "병목=MySQL"이 앱 정황 추론에 머물렀음.
- 판별 뼈대: Little's Law L=λ·W. 천장에서 L=40·λ≈280이면 W≈143ms인데 건강 시 점유는 ~6ms(그럼 40개로 ~6,600rps 가능) → 풀이 처리량을 자른 게 아니라 W가 부푼 것. 남은 질문 = W를 부풀린 게 풀 경합인지 락인지 CPU인지. 샘플러가 acquire(획득 대기)와 usage(점유)를 마이크로미터 누적 카운터 델타로 분리 + mysqld CPU·Threads_running·InnoDB 락 채집.
- 방법(기존 자산 재사용, 새 하네스 금지): `load-test/aws/run-pool-arms.sh`(재시작→warmup→샘플러→ramp) + `sample-pool.sh`. 변인 = `--datasource.mysql-jpa.main.maximum-pool-size` ∈ {40,80,10}(코드 수정 없이 CLI relaxed binding). 고정 = R8 2노드(app c6i.xlarge / infra m6i.large)·`ranking-read.js`(warmup 후 50→100→200→400/s)·같은 시드(랭킹 ~20만 행). 산출물 `load-test/results/exp-pool/`.
- 결과: 처리량 143.1(pool10)/142.2(40)/140.4(80) rps — 풀 키울수록 미세 하락. p95 759/954/1995ms·dropped 53/126/493 — 지연만 2~4배 악화. 천장에서 점유 17/119/231ms·mysqld CPU ~187/123/198%·Threads_running 10/15/60. **InnoDB 락 3암 전 구간 0**. 처리량 천장은 셋 다 ~2,000~2,400 stmt/s(MySQL CPU)에서 동일하게 멈춤.
- 고른 것 & 왜: 가설 기각. 풀 소진은 증상(MySQL이 못 빠져 acquire 폭발) — 슬롯 수가 처리량을 자른 적 없음(건강 구간 사용 커넥션 0~1개). 락 아님(순수 SELECT=MVCC, 락 0). 원인 = 2 vCPU를 MySQL·Redis·Kafka가 공유하는 박스의 CPU 포화. 읽기 경로는 요청당 SELECT 2개(인덱스 페이지 + IN 일괄)로 N+1 없음 — 스펙 대비 정상 범위 천장.
- 트레이드오프 / 남은 리스크: 천장을 올리는 레버는 ① CPU 증설(비싼 일을 더 센 기계로) ② 읽기를 MySQL에서 걷어내기(Stage 3 ZSET, ZREVRANGE O(log N)로 왕복 제거) — 이 실험은 ②의 근거. CPU 없이 소폭 회수 가능한 부차 요인: Redis/Kafka DB박스 분리, 깊은 페이지 OFFSET + score DESC·product_id ASC filesort. 이번 실험은 Stage 1↔3 비교표의 좌변(MySQL 버전=풀·튜닝 불가 CPU-바운드 천장)을 실측으로 고정.
- 하네스 버그(수정): `run-pool-arms.sh`가 `curl | awk '...exit'`에서 SIGPIPE로 curl이 죽어 `set -e`+pipefail이 스크립트를 중단(EXIT 23) → curl 출력을 변수로 받은 뒤 awk 하도록 수정. 백그라운드 비로그인 셸 PATH에 k6 없어 `/opt/homebrew/bin` 추가.
