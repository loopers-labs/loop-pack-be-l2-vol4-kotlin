# Round 7 구현 체크리스트 — Step 1 · Step 2 (event 기반)

> 원문: `00-requirements.html` / 상세 계획: `07-implementation-plan.html`
> 브랜치: `feature/week07-event-driven` (origin/shoeone96 에서 분기)
> 매핑: 과제 Step 1 = 계획 STEP 1 · 과제 Step 2 = 계획 STEP 4~5 (STEP 2~3 은 전환 근거 측정)
> Step 3(선착순 쿠폰)은 실험 기반 진화로 진행 — 상세: `08-coupon-issue-experiment-plan.html` · `experiments/PLAN.md`

## Step 1 — ApplicationEvent 경계 분리

- [ ] **1. Event vs Command 분류** — 도메인 동작을 명령/사실로 구분. 판단 기준 5문항(핵심 불변식? / 실패해도 본 tx 성공? / 외부 I/O? / 순서 의존? / 디커플 가치?)으로 분리·유지 근거 문서화
- [ ] **2. 주문–결제 플로우 분리** — 부가 로직(유저 행동 로깅, 알림 등)을 ApplicationEvent 로 분리
- [ ] **3. 좋아요–집계 eventual consistency** — ApplicationEvent 분리는 기존 구현 확인됨(`LikeEventHandler`·`ProductLikeCountEventHandler`: `@Async @TransactionalEventListener(AFTER_COMMIT)` + `REQUIRES_NEW`, `@EnableAsync` 적용됨). 이번 라운드는 이 위에 outbox 발행(#9) + streamer `event_handled` 멱등 집계(#11)로 확장하고, 분리/유지 근거를 #1·#5 문서화에 포함
- [ ] **4. 유저 행동 로깅 이벤트** — 조회·클릭·좋아요·주문에 대한 서버 레벨 로깅을 이벤트로 발행
- [ ] **5. 리스너 phase 선택 정당화** — 트랜잭션 결과와의 상관관계에 따라 phase 선택, 근거 답변 가능하게 (보존 질문)

## Step 2 — Kafka 파이프라인

- [ ] **6. 전파 대상 선별** — Step 1 이벤트 중 시스템 간 전파가 필요한 것만 Kafka 로
- [ ] **7. 토픽 설계** — `catalog-events`(key=productId, 상품·재고·좋아요) / `order-events`(key=orderId, 주문·결제) / 조회·클릭 등 유저 행동은 새 토픽 신설(예: `user-action-events`, key=productId) — 조회 수 집계는 원자적 증가 upsert
  - 순서 의존 분석(문서화): 정합성이 파티션 순서에 의존하는 집계 없음 — delta 집계(좋아요·판매량·조회 수)는 교환법칙 성립, 상태형 이벤트는 #12 version 가드가 방어, 쿠폰 선착순은 단일 파티션 순차 소비가 담당. key 지정은 비용 0 + 요구사항 합격 기준에 명시되어 유지
- [ ] **8. Producer 설정** — commerce-api에 `modules:kafka` 의존성 추가 + 앱 `application.yml`에 `acks=all`·`enable.idempotence=true` 설정 (베이스 `modules/kafka/kafka.yml`은 수정 금지 — 앱 yaml 병합으로 주입)
- [ ] **9. Transactional Outbox** — `outbox_event` 를 비즈니스와 같은 tx 로 INSERT + 폴링 릴레이(init→sent)로 at-least-once 발행 보장. eventId 는 이벤트 생성 시 UUID
- [ ] **10. Consumer = commerce-streamer 앱** — manual ack(`AckMode.MANUAL`): 처리 성공 후에만 offset commit. 베이스 설정 `auto.offset.reset=latest` 주의(신규 그룹은 기동 전 발행분을 건너뜀) — 검증 시 컨슈머 먼저 기동 or earliest 검토
- [ ] **11. 멱등 처리** — `event_handled(event_id PK)` INSERT, 중복이면 skip. 비즈니스 write 와 같은 로컬 tx
- [ ] **12. 최신성 가드** — `version`/`updated_at` 비교로 늦게 온 옛 이벤트가 최신 집계를 덮어쓰지 않게
- [ ] **13. 집계 upsert** — 좋아요 수·판매량·조회 수를 `product_metrics` 에 upsert (event_handled 와 같은 tx)
- [ ] **14. 보존 질문** — "왜 event_handled 와 유저 행동 로그 테이블을 분리하는가" 설계 근거로 답변

## Step 3 — Kafka 기반 선착순 쿠폰 발급 (실험 기반 진화)

> 구현 방식: DB → Redis → Kafka 로 부하 실측 근거를 만들며 진화. 운영 문서 `experiments/PLAN.md`, 실험 1건마다 `experiments/EXP-NN-<이름>.md` 기록.
> 정합성 불변식(전 단계 하드 제약): 발급 ≤ 한도(100) · userId 중복 0 · 거절도 결과 조회 가능. 확정 게이트: time_to_decision ≤ 10초.

- [ ] **15. Phase 0-b — 뼈대·실험 준비 (서버 불필요, 즉시 가능)**
  - [ ] `Coupon`에 `total_quantity`/`issued_count` 추가 + `UserCouponGrantedType.FIRST_COME` 추가
  - [ ] `POST /api/v1/coupons/issue` (동기, Phase 1용) — 매진 시 `ConflictException(SOLD_OUT)`
  - [ ] `runConcurrently` 정합성 테스트: 한도 100 + 동시 1,000 → 정확히 100건·중복 0
  - [ ] commerce-api Dockerfile + compose(profile: core/p3/p4) + 시드(유저 1만+, 쿠폰 2종: 한도 100 스파이크용 / 한도 십만 지속 경합용)
  - [ ] k6 시나리오 2종: S1 스파이크(10초 내 1만 요청) / S2 계단(100→200→400→800/s 각 3~5분)
  - [ ] actuator + micrometer-registry-prometheus 의존성
- [ ] **16. Phase 0-a — 홈서버 (서버 켠 뒤, 물리 작업 포함)**
  - [ ] 네트워크 실태 검증: 현재 IP / ufw status / docker publish 포트 / 외부 포트스캔 (지난 k6가 통했던 경로 확인)
  - [ ] 공유기 뒤 이사 + 내부 IP 고정 → 서버 스펙 기록(nproc, free)
  - [ ] 토폴로지 기동: 홈서버 = SUT + exporter만 / 맥 = k6 + Prometheus(LAN scrape 5초) + Grafana
- [ ] **17. Phase 1 — DB-only 3변형 부하 비교** — A 비관(FOR UPDATE) / B 조건부 원자 UPDATE / C 낙관(@Version)+재시도. 공통 최종 방어 `uk_user_coupon`. 종료: DB-only 구조적 상한 확정
- [ ] **18. Phase 2 — 병목 진단 + 튜닝** — 진단 사다리(Tomcat→Hikari→row lock→DB CPU→앱). 가상 스레드 on/off 비교(pinning 관찰). Hikari 10→20→30 × Tomcat 200→400, MySQL CPU 80% 가드레일. 종료: 튜닝 상한 + 병목의 이름 확정
- [ ] **19. Phase 3 — Redis 선검증** — Lua로 수량+userId 원자 판정 → 통과분만 DB 동기 발급(DB=진실 원천). 부하 중 redis kill 정합성 검증. 종료: 개선 폭 + 새 병목 명명
- [ ] **20. Phase 4 — Kafka 지연 발급 (과제 MUST 합격선)**
  - [ ] `POST` → `coupon-issue-requests`(key=couponId, 파티션 1) 직발행 + `requestId` 즉시 응답 (Outbox 불필요 근거: 묶을 본 tx 없음 — 문서화)
  - [ ] 단일 컨슈머 순차 처리: 수량 확인 → 발급/거절 → `coupon_issue_result` 저장(PK=requestId, PENDING→ISSUED/REJECTED, requested_at/decided_at)
  - [ ] `GET /api/v1/coupons/issue/{requestId}` polling 조회
  - [ ] 멱등: requestId insert-first — 재전송에도 중복 차감 불가. 부하 중 consumer 재시작 → 중복 0 검증
  - [ ] k6 부하에 polling 포함(POST → 1초 간격 GET, 최대 10초) → time_to_decision 게이트 판정
  - [ ] 동시성 합격: 1만 동시 요청 → 발급 ≤ 100 · 중복 0 · 거절 조회 가능
- [ ] **21. Phase 5 — Resilience + Rate Limit** — 장애 주입(Redis/Kafka/consumer/DB down) 대응 결정. rate limit = 실측 용량 × 0.7, Bucket4j 인메모리, 429. lag 알람 임계 = consumer 처리량 × 10초

## Nice-To-Have (Must-Have 완료 후에만)

- [ ] Consumer Group 분리 — 관심사별(집계/쿠폰/로깅) 독립 처리
- [ ] Consumer 배치 처리 — 레코드 묶음 소비로 처리량 향상
- [ ] DLQ 구성 — 반복 실패 메시지 격리

## 구현 완료 후 — Technical Writing (제출 필수 산출물)

- [ ] GitHub Issue 4포맷 중 1개 작성 (Design Doc / Retrospective / Challenge Story / Benchmark Report)
- [ ] 블로그 글 — TL;DR 필수, "무엇을 했다"가 아니라 "왜 그렇게 판단했나" 중심
