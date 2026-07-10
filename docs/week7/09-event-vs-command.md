# Event vs Command 판정 — ApplicationEvent 경계 분리 근거

> checklist #1(분류 근거)·#5(리스너 phase 정당화) 산출물.
> 판단 기준 5문항: ① 도메인 핵심 불변식인가 ② 실패해도 본 트랜잭션은 성공해야 하는가 ③ 외부 I/O 인가 ④ 순서 의존이 있는가 ⑤ 강결합 제거 가치가 있는가

## 판정 표

| 후보 로직 | ①불변식 | ②실패해도 본 tx 성공 | ③외부 I/O | ④순서 의존 | ⑤디커플 가치 | 판정 |
|---|---|---|---|---|---|---|
| 재고 차감 · 쿠폰 사용 · 주문 저장 | Y | N | N | Y | 낮음 | **Command — 본 트랜잭션 유지** |
| 주문 생성 → 외부 집계·전파 | N | Y | Y (Kafka 예정) | N | 높음 | **Event + Outbox** (`OrderCreatedEvent`) |
| 주문/조회/좋아요 → 유저 행동 로그 | N | Y | N | N | 높음 | **Event — AFTER_COMMIT 메모리** (유실 허용, outbox 불필요) |
| 좋아요 → like_count · 이력 | N | Y | N | N | 높음 | **Event 유지 (기존 구현)** — 좋아요 사실 자체는 outbox 병행 승격 |
| 결제 실패 → `cancelAndCompensate` (`PaymentService` → `OrderFacade` 결합) | **Y** | **N** | N | Y | 중 | **Command 유지** (아래 상세) |
| 결제 성공 → `confirmOrder` | Y | N | N | Y | 낮음 | **Command 유지** — 주문 상태 전이 = 불변식 |
| 클릭 로깅 | — | — | — | — | — | **보류** — 클릭을 수신하는 endpoint 자체가 없음 (신설 시 재판정) |

### 결제 보상을 이벤트로 빼지 않는 이유

재고·쿠폰 복원은 정합성 그 자체라 유실되면 재고가 영구 결손된다. AFTER_COMMIT 이벤트로 빼면 리스너 유실 시 복구 경로가 없다. 현재 구조는 보상 실패 시 결제 fail 마킹과 함께 롤백되고 `PaymentReconciler` 폴링이 재시도해 주는 안전망이 있다. outbox 기반 order-events 소비가 생기는 Kafka 단계에서 승격을 재검토한다.

## 리스너 phase 선택 근거 (checklist #5)

| 리스너 | 조합 | 근거 |
|---|---|---|
| `OutboxEventHandler` | `BEFORE_COMMIT` + 어노테이션 없음(본 tx 참여) | outbox 는 비즈니스 write 와 **원자적**이어야 발행 보장이 성립. 적재 실패 시 본 tx 도 함께 롤백(유실 불허). `@Async`/`REQUIRES_NEW` 를 붙이면 별도 tx 로 빠져 원자성이 깨지므로 금지 |
| `LikeEventHandler` · `ProductLikeCountEventHandler` · `UserActionLogEventHandler` | `@Async` + `REQUIRES_NEW` + `AFTER_COMMIT` | 커밋된 사실에만 반응(롤백된 좋아요/주문을 집계하지 않음) + 본 tx 지연·실패에 영향 없음. `REQUIRES_NEW` 는 AFTER_COMMIT 시점엔 원본 tx 리소스가 커밋 후라 새 tx 가 필요하기 때문 |

주의: `@TransactionalEventListener` 는 트랜잭션 밖에서 publish 되면 조용히 스킵된다(`fallbackExecution=false` 기본). 발행 지점이 `@Transactional` 내부임을 통합 테스트(`OrderOutboxIntegrationTest`)가 고정한다.
