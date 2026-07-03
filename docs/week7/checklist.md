# Round 7 구현 체크리스트 — Step 1 · Step 2 (event 기반)

> 원문: `00-requirements.html` / 상세 계획: `07-implementation-plan.html`
> 브랜치: `feature/week07-event-driven` (origin/shoeone96 에서 분기)
> 매핑: 과제 Step 1 = 계획 STEP 1 · 과제 Step 2 = 계획 STEP 4~5 (STEP 2~3 은 전환 근거 측정)
> Step 3(선착순 쿠폰)은 실험 기반 진화로 진행 — 상세: `08-coupon-issue-experiment-plan.html` · `experiments/PLAN.md`

## Step 1 — ApplicationEvent 경계 분리

- [x] **1. Event vs Command 분류** — 판단 기준 5문항으로 분리·유지 근거 문서화 완료: `09-event-vs-command.md` (결제 보상 `cancelAndCompensate` 는 Command 유지 판정) — PR [#7](https://github.com/shoeone96/loop-pack-be-l2-vol4-kotlin/pull/7)
- [x] **2. 주문–결제 플로우 분리** — `OrderCreatedEvent` 발행 + `UserActionLogEventHandler`(AFTER_COMMIT 비동기) 분리 — PR #7
- [x] **3. 좋아요–집계 eventual consistency** — 기존 AFTER_COMMIT 리스너 위에 `ProductLikedEvent`/`ProductUnlikedEvent` outbox 승격(#9) + streamer 멱등 집계(#11) 완료 — PR #7
- [x] **4. 유저 행동 로깅 이벤트** — 조회·좋아요·주문 → `user_action_log` (클릭은 수신 endpoint 부재로 보류) — PR #7
- [x] **5. 리스너 phase 선택 정당화** — BEFORE_COMMIT(outbox, 본 tx 참여) vs AFTER_COMMIT(부가 처리, 유실 허용) 근거: `09-event-vs-command.md` — PR #7

## Step 2 — Kafka 파이프라인

- [x] **6. 전파 대상 선별** — `OutboxPublishable` 마커로 타입 표현: 주문·좋아요만 outbox 승격, 조회는 유실 허용 직접 발행 — PR #7
- [x] **7. 토픽 설계** — `catalog-events`(key=productId) / `order-events`(key=orderId) / `user-action-events`(key=productId) 신설, 조회 수는 `ON DUPLICATE KEY UPDATE` 원자적 증가 upsert — PR #7
  - 순서 의존 분석(문서화): 정합성이 파티션 순서에 의존하는 집계 없음 — delta 집계(좋아요·판매량·조회 수)는 교환법칙 성립, 상태형 이벤트는 #12 version 가드가 방어, 쿠폰 선착순은 단일 파티션 순차 소비가 담당. key 지정은 비용 0 + 요구사항 합격 기준에 명시되어 유지
- [x] **8. Producer 설정** — `modules:kafka` 의존성 + 앱 yaml `acks=all`·`enable.idempotence=true` 주입(베이스 수정 없음) — PR #7
- [x] **9. Transactional Outbox** — BEFORE_COMMIT 적재 + `OutboxRelay`(fixedDelay 1s, `(status,id)` 인덱스, LIMIT 100, 실패 시 중단·재시도, SENT 3일 purge) — 실 브로커 e2e 로 INIT→SENT 검증 — PR #7
- [x] **10. Consumer = commerce-streamer 앱** — 3토픽 배치 리스너 + manual ack(처리 성공 후 offset commit). latest 주의사항 준수(검증 시 컨슈머 기동 후 발행) — PR #7
- [x] **11. 멱등 처리** — `event_handled(event_id PK)` + 집계 같은 로컬 tx. e2e 로 중복 eventId 재전달 → 카운트 불변 확인 — PR #7
- [x] **12. 최신성 가드** — 이번 이벤트는 전부 delta 증감(교환법칙 성립)이라 **생략 판정**, 상태형 이벤트 도입 시 추가 — 근거 PR #7
- [x] **13. 집계 upsert** — 좋아요 수·판매량·조회 수 → `product_metrics` 원자적 증가 upsert (event_handled 와 같은 tx) — PR #7
- [x] **14. 보존 질문** — event_handled(컨슈머 멱등 키, 소비 측 소유) vs user_action_log(분석·감사 원본, 발행 측 소유)는 목적·수명·소유가 달라 분리 — `09-event-vs-command.md`·PR #7

## Step 3 — Kafka 기반 선착순 쿠폰 발급 (실험 기반 진화)

> 구현 방식: DB → Redis → Kafka 로 부하 실측 근거를 만들며 진화. 운영 문서 `experiments/PLAN.md`, 실험 1건마다 `experiments/EXP-NN-<이름>.md` 기록.
> 정합성 불변식(전 단계 하드 제약): 발급 ≤ 한도(100) · userId 중복 0 · 거절도 결과 조회 가능. 확정 게이트: time_to_decision ≤ 10초.

- [ ] **15. Phase 0-b — 뼈대·실험 준비 (서버 불필요, 즉시 가능)**
  - [x] `Coupon`에 `total_quantity`/`issued_quantity` 추가 + `UserCouponGrantedType.FIRST_COME` 추가 — PR [#6](https://github.com/shoeone96/loop-pack-be-l2-vol4-kotlin/pull/6)
  - [x] `POST /api/v1/coupons/issue` (동기, Phase 1용) — 매진 시 `ConflictException(SOLD_OUT)`, 비관적 락(inventory 선례) — PR #6
  - [x] `runConcurrently` 정합성 테스트: 한도 100 + 동시 300(Hikari 풀 10 기준 조정, 근거 PLAN.md) → 정확히 100건·중복 0 — PR #6
  - [x] commerce-api Dockerfile(멀티스테이지 jdk→jre) + `load-test/sut-compose.yml`(profile core/p3/p4, 전 포트 127.0.0.1 바인딩) + 시드 `load-test/coupon-perf-seed.sql`(유저 1만 cpuser00001~, 쿠폰 90001=한도100 스파이크 / 90002=한도10만 지속) — **런타임 dry-run 검증 완료**(부팅→시드→스모크→S1/S2). 부팅 실패 2건(ENTRYPOINT 인자 유실 / dev 프로파일 logback 크래시) 수정, S1 발급 정확히 100·중복 0 — 근거 `experiments/WRITING-LOG.md` 결정 10-보강
  - [x] k6 시나리오 2종: `coupon-issue-spike.js` S1(1000/s×10s=1만, distinct 유저) / `coupon-issue-step.js` S2(ramping 100→200→400→800/s) + 보너스 `harness-ceiling-smoke.js`(경로 천장 검사) — node --check 통과
  - [x] actuator + micrometer-registry-prometheus 의존성 — `:supports:monitoring` 경유로 이미 commerce-api runtimeClasspath 에 존재(actuator 3.4.4 + micrometer-registry-prometheus 1.14.5), management.server.port=8081, http.server.requests 히스토그램 on. 신규 작업 불필요, 검증만
- [x] **16. Phase 0-a — EC2 SUT 기동 (홈서버 검토 → EC2 확정, WRITING-LOG 결정 11)**
  - [x] EC2 `m5.xlarge`(4 vCPU/16 GiB, ap-northeast-2a) 기동 + SG를 내 공인 IP `/32`로 제한 + 컨테이너 `127.0.0.1` bind → 공인 노출 0 (홈서버 ufw 우회·공유기 이사·DHCP 문제 원천 소멸)
  - [x] EC2에서 git clone→빌드(x86_64 네이티브) → `core` 스택 기동 → 시드 → 스모크
  - [x] 토폴로지: EC2 = SUT + k6 co-located (하네스 천장 스모크 ≥4000/s = 포화점 ×16 확인 → co-located 유효). 관측 스택은 필요 시 SUT 밖으로(원칙 유지). m5는 고정 성능이라 절대 수치 확정 가능
- [ ] **17. Phase 1 — DB-only 3변형 부하 비교** — A 비관(FOR UPDATE) **✅ 완료(EXP-01, ~250 req/s·정합성 완벽·병목=단일행 락 직렬화, CPU 아님)** / B 조건부 원자 UPDATE **✅ 완료(EXP-02, S2 무릎 실측 ~550~590/s·500 폭풍 94%↓·정합성 완벽, 병목 락 대기→CPU로 이동, 스파이크 decision 꼬리는 동급 — 동기 설계 한계 재확인)** / C 낙관(@Version)+재시도. 공통 최종 방어 `uk_user_coupon`. 종료: DB-only 구조적 상한 확정
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
