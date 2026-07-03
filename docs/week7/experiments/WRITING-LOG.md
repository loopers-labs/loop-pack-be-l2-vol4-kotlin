# Writing Log — 선착순 쿠폰 발급 시스템 고도화

> Round 7 Technical Writing(GitHub Issue 1 + 블로그) 소재 축적 노트.
> 원칙: "무엇을 했다"가 아니라 **"왜 그렇게 판단했나"** 를 결정 시점에 기록한다. 나중에 재구성하면 근거가 미화된다.
> 실측 수치는 `EXP-NN-*.md`가 원본 — 여기엔 서사와 판단만.

## 글의 뼈대 (가설)

처음부터 Kafka로 가지 않는다. **가장 단순한 동기+DB 구현에서 시작해, 부하 실측으로 병목에 이름을 붙인 뒤에만 다음 단계(Redis → Kafka)로 전환**한다. 각 전환마다 "무엇이 한계였고, 새 구조가 그 한계의 무엇을 바꾸는가"를 수치로 답한다.

- 하드 불변식(전 단계 공통): 발급 ≤ 한도(100) · userId 중복 0 · 거절도 결과 조회 가능
- 확정 게이트: time_to_decision ≤ 10초
- 전체 실험 설계: [PLAN.md](PLAN.md) / 배경: `../08-coupon-issue-experiment-plan.html`

---

## 2026-07-02 — Phase 0-b 베이스라인 구현 (PR #6)

PR: https://github.com/shoeone96/loop-pack-be-l2-vol4-kotlin/pull/6 (2026-07-03 merged, `479046f`)

### 결정 1 — 베이스라인은 동기 + 비관적 락

- **왜 비관적 락**: 선착순은 충돌이 확실한(경합이 기본인) 워크로드. 낙관적 락은 충돌 시 재시도 폭풍이 되고, 조건부 UPDATE는 엔티티 dirty checking 관례를 벗어남. 코드베이스에 inventory `@Lock(PESSIMISTIC_WRITE)` 선례가 있어 같은 패턴 채택.
- **단, 이 선택은 잠정**: Phase 1에서 A(비관) / B(조건부 원자 UPDATE) / C(낙관+재시도) 3변형을 같은 부하로 비교해 실측으로 재평가한다. 베이스라인의 역할은 "정합성이 보장되는 가장 단순한 기준점"이지 최종 답이 아님.
- 전략 패턴 등 변형 추상화는 지금 만들지 않음(YAGNI) — 변형은 Phase 1에서 필요해질 때.

### 결정 2 — `totalQuantity`는 nullable, null이면 발급 거부

- 기존 쿠폰은 전부 관리자 지급 전용 → 한도 개념이 없음. `Long? = null`로 하위 호환.
- null 쿠폰에 self-issue 시도 → `NOT_ISSUABLE`(400). **null = 무제한이 아니라 "선착순 발급 대상 아님"** — 관리자 지급 전용 쿠폰을 아무나 가져가는 구멍 차단.
- 검증 순서: NOT_ISSUABLE → EXPIRED → SOLD_OUT → 증가. grant의 "존재→만료→중복" 순서와 정합 유지.

### 결정 3 — 중복 방어 2겹 + 롤백에 의한 수량 원복

- 락 안에서 `existsByUserIdAndCouponId` 체크(1차) + `uk_user_coupon` unique 제약(최종 방어).
- 중복이면 예외 → 트랜잭션 전체 롤백 → **먼저 증가한 issuedQuantity도 함께 원복**. "증가 후 중복 체크" 순서라도 정합성이 깨지지 않는 이유가 트랜잭션 원자성.
- unique race의 잔여 케이스는 서비스 try-catch가 아니라 `ApiControllerAdvice`의 `DataIntegrityViolationException` 핸들러가 409로 변환(레포 관례 — 서비스에 영속성 디테일 누수 금지).

### 결정 4 — userId는 body가 아니라 인증 컨텍스트에서 (`@RequestAttribute`)

- 논의 발단: "raw 헤더(`X-USER-ID`)로 받으면 어떤가" → **반려**. 클라이언트 자칭 값은 한 명이 id를 바꿔가며 전량 수령 가능 → "userId 중복 0" 불변식이 애초에 무의미해짐.
- 현재 구조가 이미 "헤더 추출"이 맞음: `AccountHeaderAuthenticationFilter`가 로그인 헤더를 **검증한 뒤** `ACCOUNT_ID` attribute로 넘김. 컨트롤러의 `@RequestAttribute`는 검증 끝난 결과물.
- 일반화: **본인 대상(self-service) API의 식별자는 인증에서, 요청 body에서 받지 않는다.** 대조군 — grant(`/api-admin`)는 관리자가 제3자에게 지급하므로 body userId가 정당.
- 계정 존재 검증 생략 근거: issue의 userId는 인증 필터를 통과한 본인 ACCOUNT_ID라 존재가 이미 보장됨(grant는 임의 userId라 검증 필요).

### 결정 5 — application 입력은 Command 클래스로 통일 (`CouponIssueCommand`)

- 처음엔 파일 내 grant/use 선례를 따라 `issue(couponId, userId)` 개별 파라미터로 갔다가, **기존에 확정한 일반 규칙("application 유스케이스 입력은 application 소유 Command로")에 맞춰 리팩터**.
- 규칙의 근거: ① interfaces DTO가 application에 새지 않도록 입력 계약의 소유권을 application에 둠 ② 동일 타입(Long) 파라미터 swap 버그 방지 — 실제로 기존 코드에 `grant(couponId, userId, ...)` vs `use(userId, couponId, ...)` 순서 불일치가 있음.
- 기존 grant/use/cancelUse의 통일은 이 PR 스코프 밖 → 별도 리팩터 후보.

### 결정 6 — 애그리거트 경계: Coupon과 UserCoupon은 별개 (DDD 점검)

- 리뷰 중 질문: "CouponService가 UserCoupon 로직을 다뤄도 되나? 애그리거트가 이어져 있나?"
- 답: **별개 애그리거트 루트 2개, ID 참조**(`UserCoupon.couponId: Long`, JPA 연관관계 없음, 리포지토리 각각). 한 애그리거트로 묶으면 루트가 무한 증가(한도 십만이면 십만 행)하고 use/cancel까지 발급 락에 경합.
- 불변식 배치: "발급 ≤ 한도"는 `Coupon.issue()` 내부(카운터를 루트 안에 둔 이유). "userId 중복 0"은 집합 불변식(set-based invariant)이라 단일 애그리거트가 못 지킴 → 리포지토리 exists + DB unique 제약이 정석.
- 정직한 한계: **한 트랜잭션에서 두 애그리거트 수정**("1 tx = 1 aggregate" 경험칙 위반). 단일 DB 모놀리스라 의도된 완화이고, 이 규칙을 정석대로 지키는 형태가 곧 Phase 4의 이벤트 기반 지연 발급 — **실험 계획이 DDD 권장 방향으로 수렴하도록 설계돼 있음** (글에서 쓸 만한 포인트).
- 남은 개선 여지: 서비스가 `FIRST_COME`/`SYSTEM_GRANTED` 조립 지식 보유 → `coupon.issue(userId, now): UserCoupon` 팩토리 메서드가 더 DDD다움. 단 grant도 같은 스타일이라 함께 옮기는 별도 리팩터 단위로.

### 결정 7 — 동시성 테스트는 300 스레드 (1,000 아님)

- 테스트 Hikari 풀이 10이라 1,000 스레드는 증명력 추가 없이 runConcurrently 10초 하드 타임아웃 flaky 위험만 늘림. 한도(100)의 3배인 300으로 "정확히 100건 + 중복 0" 증명.
- 진짜 스파이크(10초 내 1만 요청)는 통합 테스트의 일이 아니라 **Phase 1 k6 + 실 MySQL**의 일 — 검증 수단마다 책임이 다름.
- 테스트 5종: 단건 발급 / 순차 중복 / 매진 / 300 동시 → 정확히 100 + 200 전부 SOLD_OUT / 같은 사용자 10 동시 → 1건.

### 잔가지 (글에는 안 들어갈 수도)

- 주석 정리: "코드가 왜 맞는지" 설명하는 리뷰어용 주석 3개 삭제, 타입으로 표현 불가한 null 시맨틱 주석 1개만 유지.

---

## 다음 기록 예정

- Phase 1: DB-only 3변형 실측 → `EXP-01~03`. 여기서 "비관적 락의 구조적 상한"에 숫자가 붙으면 글의 1막이 완성됨.
- Phase 2~5 각 전환의 판단 근거.
