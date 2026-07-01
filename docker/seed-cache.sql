-- 캐시 실습용 시드 데이터 (Balanced: 상품 20만, 브랜드 100)
-- 스키마는 엔티티(BaseEntity/Product/Brand/Stock/LikeCount)에 맞춰 생성.
-- local 프로파일 ddl-auto=update 이므로 앱 기동 시 drop 되지 않고 검증만 됨.
--
-- 데이터는 "실제 커머스" 느낌이 나도록 10개 카테고리로 구성한다.
--   카테고리(=FLOOR((brand_id-1)/10)): 0 패션 / 1 전자 / 2 뷰티 / 3 식품 / 4 스포츠
--                                      5 홈리빙 / 6 문구 / 7 키즈 / 8 반려동물 / 9 자동차
-- 단, 시드 간 조인이 깨지지 않도록 id 산술 관계는 그대로 유지한다.
--   brand.id            = 1..100
--   product.id          = n (1..200000)
--   product.brand_id    = ((n-1) % 100) + 1
--   stock.product_id    = n
--   like_count.product_id = n

SET SESSION cte_max_recursion_depth = 1000000;

-- ---------- DDL ----------
CREATE TABLE IF NOT EXISTS brand (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  created_at  DATETIME(6)  NOT NULL,
  updated_at  DATETIME(6)  NOT NULL,
  deleted_at  DATETIME(6)  NULL,
  name        VARCHAR(255) NOT NULL,
  description VARCHAR(255) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_brand_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS product (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  created_at  DATETIME(6)  NOT NULL,
  updated_at  DATETIME(6)  NOT NULL,
  deleted_at  DATETIME(6)  NULL,
  name        VARCHAR(255) NOT NULL,
  price       BIGINT       NOT NULL,
  description TEXT         NOT NULL,
  brand_id    BIGINT       NOT NULL,
  PRIMARY KEY (id),
  KEY idx_product_brand_id (brand_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS stock (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  created_at  DATETIME(6) NOT NULL,
  updated_at  DATETIME(6) NOT NULL,
  deleted_at  DATETIME(6) NULL,
  product_id  BIGINT      NOT NULL,
  quantity    INT         NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_stock_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS like_count (
  id         BIGINT NOT NULL AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  count      INT    NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_like_count_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- 기존 시드 정리 (재실행 안전) ----------
TRUNCATE TABLE like_count;
TRUNCATE TABLE stock;
TRUNCATE TABLE product;
TRUNCATE TABLE brand;

-- ---------- 시드 삽입 ----------
SET autocommit = 0;
SET unique_checks = 0;

-- 1) brand 100 (카테고리별 10개씩, 실제 브랜드처럼 보이는 고유 상호)
--    name        : id 순서대로 ELT 로 매핑 (1~10 패션, 11~20 전자, ... 91~100 자동차)
--    description : "카테고리 전문 브랜드 · 슬로건" 형태로 id 마다 다르게 구성
INSERT INTO brand (id, created_at, updated_at, deleted_at, name, description)
WITH RECURSIVE seq AS (
  SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 100
)
SELECT
  n,
  NOW(6) - INTERVAL FLOOR(RAND() * 525600) MINUTE,
  NOW(6),
  NULL,
  ELT(n,
    -- 1~10 패션
    '어반스티치','모먼트룩','데일리핏','코튼하우스','레이어드','트웬티노트','베이직무드','페이브먼트','클로젯서울','노르딕라인',
    -- 11~20 전자
    '테크노바','픽셀라이트','큐브사운드','넥스기어','볼트엣지','하이파이브','제로딜레이','코어웨이브','루멘텍','스마트독',
    -- 21~30 뷰티
    '글로우랩','퓨어셀','데일리스킨','보타닉뷰','코랄리스','모이스처바','라인업코스메틱','누드베이스','비건글로우','센슈얼',
    -- 31~40 식품
    '모닝브레드','그린테이블','한입가득','청정원미','데일리브루','오가닉팜','스윗아워','미식상점','곳간','바른먹거리',
    -- 41~50 스포츠
    '액티브라인','페이스메이커','마운틴코어','라이드업','스웻기어','헬스노트','트레일러너','아쿠아핀','윈드브레이크','점프스타트',
    -- 51~60 홈리빙
    '무드홈','우드앤코','코지룸','데일리리빙','화이트박스','라탄하우스','슬립웰','키친토크','라이트오프','미니멀리',
    -- 61~70 문구
    '책방오후','페이지원','잉크앤페이퍼','노트라이프','문장수집가','데스크메이트','컬러펜슬','리딩룸','글월','페이퍼팩토리',
    -- 71~80 키즈
    '아이놀자','토이박스','베이비무드','키즈랜드','뽀송데이','꿈동산','플레이타임','리틀스텝','해피블럭','모모키즈',
    -- 81~90 반려동물
    '멍냥상점','펫츠클럽','꼬리살랑','그루밍데이','댕댕마트','묘한하루','펫프렌즈','산책가자','폭신발자국','애니멀하우스',
    -- 91~100 자동차/공구
    '드라이브웍스','기어샵','카케어프로','볼트앤너트','로드메이트','핸디툴즈','오토라인','파워그립','메탈워크','정비왕'
  ),
  CONCAT(
    ELT(FLOOR((n - 1) / 10) + 1,
      '패션','전자제품','뷰티','식품','스포츠','홈리빙','문구','키즈','반려동물','자동차용품'),
    ' 전문 브랜드 · ',
    ELT((n % 8) + 1,
      '합리적인 가격과 검증된 품질로 사랑받고 있습니다.',
      '트렌드를 선도하는 디자인을 선보입니다.',
      '매일의 일상에 작은 만족을 더합니다.',
      '엄선한 소재만을 고집합니다.',
      '오랜 노하우로 신뢰를 쌓아왔습니다.',
      '실용성과 감각을 동시에 담았습니다.',
      '고객 후기로 증명된 베스트셀러 브랜드입니다.',
      '새로운 라이프스타일을 제안합니다.')
  )
FROM seq;
COMMIT;

-- 2) product 200,000  (brand 당 상품 수를 롱테일로 편중)
--    brand_id    : 균등 분배(브랜드당 2000개)가 아니라, 멱함수로 치우친 분포로 뽑는다.
--                  단, "인기 순위"가 brand_id 순서(1,2,3,4...)와 일치하면 부자연스러우므로
--                  brand 마다 무작위 순위(rnk, 1~100)를 먼저 부여해 섞는다.
--                    rnk    = FLOOR(POW(RAND(), 2.0) * 100) + 1   -- 0쪽으로 쏠리는 인기 순위
--                    brand  = 그 순위에 무작위로 배치된 브랜드      -- 어느 id가 인기일지는 매번 랜덤
--                  product.id(=n) 는 그대로라 stock/like_count/likes 조인은 영향 없음.
--    name        : [브랜드명] 형용사 + (카테고리별 품목) + 모델/세대
--    price       : 카테고리별 현실적인 가격대, 100원 단위로 반올림
--    description : 브랜드/품목 + 회전하는 상품 카피
INSERT INTO product (id, created_at, updated_at, deleted_at, name, price, description, brand_id)
WITH RECURSIVE seq AS (
  SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 200000
),
brand_rank AS (
  -- 100개 브랜드를 무작위로 1~100 순위에 배치 (인기도와 id 상관관계 제거)
  SELECT brand_id, ROW_NUMBER() OVER (ORDER BY rk) AS rnk
  FROM (SELECT id AS brand_id, RAND() AS rk FROM brand) t
)
SELECT
  s.n,
  NOW(6) - INTERVAL FLOOR(RAND() * 525600) MINUTE,
  NOW(6),
  NULL,
  CONCAT(
    b.name, ' ',
    -- 형용사
    ELT((s.n % 12) + 1,
      '프리미엄','클래식','모던','베이직','라이트','프로','에디션','시그니처','데일리','컴팩트','울트라','에코'), ' ',
    -- 카테고리별 품목
    CASE FLOOR((b.id - 1) / 10)
      WHEN 0 THEN ELT(((s.n DIV 12) % 10) + 1, '오버핏 셔츠','슬림 데님','니트 가디건','코튼 후디','린넨 자켓','와이드 슬랙스','베이직 티셔츠','패딩 점퍼','플리츠 스커트','체크 셔츠')
      WHEN 1 THEN ELT(((s.n DIV 12) % 10) + 1, '무선 이어폰','블루투스 스피커','기계식 키보드','게이밍 마우스','노트북 스탠드','USB-C 허브','보조배터리','웹캠','모니터암','LED 모니터')
      WHEN 2 THEN ELT(((s.n DIV 12) % 10) + 1, '수분 크림','진정 토너','클렌징 폼','립 틴트','선크림','앰플 세럼','마스크팩','아이섀도우','쿠션 파운데이션','핸드크림')
      WHEN 3 THEN ELT(((s.n DIV 12) % 10) + 1, '드립 커피','수제 그래놀라','유기농 잼','곡물 시리얼','콜드브루','수제 쿠키','견과류 믹스','자연숙성 꿀','엑스트라버진 올리브유','수제 초콜릿')
      WHEN 4 THEN ELT(((s.n DIV 12) % 10) + 1, '러닝화','요가 매트','덤벨 세트','등산 배낭','자전거 헬멧','스포츠 양말','폼롤러','워터보틀','트레이닝 밴드','스포츠 타월')
      WHEN 5 THEN ELT(((s.n DIV 12) % 10) + 1, '아로마 캔들','무드등','수납 박스','면 이불','우드 트레이','세라믹 머그','러그','암막 커튼','수건 세트','디퓨저')
      WHEN 6 THEN ELT(((s.n DIV 12) % 10) + 1, '만년필','가죽 노트','데스크 패드','형광펜 세트','독서대','자석 북마크','다이어리','젤펜 세트','클립보드','스티커 팩')
      WHEN 7 THEN ELT(((s.n DIV 12) % 10) + 1, '원목 블록','애착 인형','입체 퍼즐','보드게임','자석 타일','역할놀이 세트','그림 동화책','스케치북','클레이 세트','RC카')
      WHEN 8 THEN ELT(((s.n DIV 12) % 10) + 1, '건식 사료','수분 간식','자동 급수기','쿠션 방석','산책 목줄','노즈워크 장난감','펫 캐리어','배변패드','저자극 샴푸','발톱깎이')
      ELSE      ELT(((s.n DIV 12) % 10) + 1, '차량용 고속충전기','블랙박스','차량용 공구 세트','전동 드릴','와이퍼','차량용 방향제','극세사 세차 타월','자동차 커버','복스알 렌치 세트','줄자')
    END, ' ',
    -- 모델/세대
    ELT((s.n % 6) + 1, 'Lite','Pro','Max','Mini','Plus','Air'), ' ',
    ((s.n DIV 7) % 5) + 1, '세대'
  ),
  -- 카테고리별 가격대 (100원 단위 반올림)
  FLOOR(
    CASE FLOOR((b.id - 1) / 10)
      WHEN 0 THEN 15000 + RAND() * 185000   -- 패션      15,000 ~ 200,000
      WHEN 1 THEN 20000 + RAND() * 480000   -- 전자      20,000 ~ 500,000
      WHEN 2 THEN  8000 + RAND() *  72000   -- 뷰티       8,000 ~  80,000
      WHEN 3 THEN  4000 + RAND() *  46000   -- 식품       4,000 ~  50,000
      WHEN 4 THEN 10000 + RAND() * 290000   -- 스포츠     10,000 ~ 300,000
      WHEN 5 THEN  9000 + RAND() * 141000   -- 홈리빙      9,000 ~ 150,000
      WHEN 6 THEN  3000 + RAND() *  57000   -- 문구       3,000 ~  60,000
      WHEN 7 THEN  7000 + RAND() * 123000   -- 키즈       7,000 ~ 130,000
      WHEN 8 THEN  5000 + RAND() *  95000   -- 반려동물    5,000 ~ 100,000
      ELSE         8000 + RAND() * 242000   -- 자동차/공구 8,000 ~ 250,000
    END / 100) * 100,
  CONCAT(
    b.name, ' · ',
    ELT((s.n % 6) + 1,
      '꼼꼼한 마감과 실사용 후기로 검증된 제품입니다.',
      '데일리로 부담 없이 사용하기 좋습니다.',
      '선물용으로도 인기가 많은 스테디셀러입니다.',
      '합리적인 가격대의 가성비 아이템입니다.',
      '한정 수량으로 준비된 인기 상품입니다.',
      '재구매율 높은 베스트 아이템입니다.')
  ),
  b.id
FROM (
  SELECT n, FLOOR(POW(RAND(), 2.0) * 100) + 1 AS rnk
  FROM seq
) s
JOIN brand_rank br ON br.rnk = s.rnk
JOIN brand b ON b.id = br.brand_id;
COMMIT;

-- 3) stock 200,000 (product_id = n)
INSERT INTO stock (created_at, updated_at, deleted_at, product_id, quantity)
WITH RECURSIVE seq AS (
  SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 200000
)
SELECT
  NOW(6),
  NOW(6),
  NULL,
  n,
  FLOOR(RAND() * 1000)
FROM seq;
COMMIT;

-- 4) like_count 200,000 (product_id = n, 0~5000 으로 likes_desc 정렬 의미부여)
INSERT INTO like_count (product_id, count)
WITH RECURSIVE seq AS (
  SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 200000
)
SELECT
  n,
  FLOOR(RAND() * 5000)
FROM seq;
COMMIT;

SET unique_checks = 1;
SET autocommit = 1;
