# Outbox 릴레이 CircuitBreaker 견고화 — 로컬 검증 기록

Round 7. `OutboxRelay`가 카프카 장애에 대응하도록 CircuitBreaker(`kafka-relay`)를 도입한 뒤, docker 카프카를 실제로 죽였다 살리며 동작을 검증한 기록이다.

## 대상 구조

```
OutboxRelay.relay() @Scheduled(1s)
  → outboxEventRepository.findByStatus(INIT, 100)
  → EventMessagePublisher.publish(topic, key=aggregateId, payload)   (domain port)
      → KafkaEventMessagePublisher @CircuitBreaker("kafka-relay")     (infrastructure)
          → kafkaTemplate.send(...).get(10s)
  → 성공분 markSent(SENT) 벌크 / 실패 시 그 지점에서 중단
```

- CB OPEN이면 `publish()`가 `CallNotPermittedException`을 **블로킹 없이 즉시** 던지고, 릴레이가 이를 구분 catch해 "발행 보류" 후 다음 폴링에서 재시도한다.
- 데이터 유실은 outbox가 막는다(발행 실패분은 INIT으로 남아 복구 후 재발행). CB의 목적은 데이터가 아니라 **운영**(카프카 장애 중 무의미한 send 시도·스레드 블로킹 차단).

## kafka-relay CB config (판단 근거)

`apps/commerce-api/src/main/resources/application.yml`

```yaml
resilience4j:
  circuitbreaker:
    configs:
      kafka-relay:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 5
        minimum-number-of-calls: 3
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 3
        record-exceptions:
          - java.lang.Exception
        ignore-exceptions: []
    instances:
      kafka-relay:
        base-config: kafka-relay
```

- **record-exceptions = `java.lang.Exception`**: 릴레이의 `publish()`는 오직 `kafkaTemplate.send().get()`만 하므로, 여기서 나오는 모든 예외가 곧 발행 실패다. 구체 타입만 나열하면 놓친 예외에서 CB가 안 열리는 리스크가 있다. poison(직렬화) 문제는 `readTree`가 `publish` 밖(릴레이)이라 무관.
- **ignore-exceptions = `[]`**: resilience4j의 `configs.default`는 모든 config의 암묵 베이스라, 명시하지 않으면 default의 feign 전용 `record/ignore-exceptions`를 상속한다. 이 경우 카프카의 `TimeoutException`이 실패로 기록되지 않아 **CB가 영원히 CLOSED가 되는 버그**가 발생했다(아래 참조). 그래서 전용 config로 default 상속을 끊었다.
- window 5 / min-calls 3 / failure-rate 50: 릴레이는 폴링당 소수 호출이라 작은 window로 빠르게 감지하고, 카프카 장애는 연속 실패라 3회면 확실히 OPEN.
- wait 10s / half-open 3: 카프카 재시작 시간을 감안한 복구 대기 + 복구 시 3건만 시험 발행(backlog 완충).

## 검증 시나리오와 결과

| 단계 | 조작 | outbox 상태 | 결과 |
|---|---|---|---|
| 1 | 정상 주문 1건 | INIT → **SENT** | 정상 발행 |
| 2 | `docker stop kafka` + 주문 3건 | **INIT 3건 유지** | 유실 0. 3회 실패 후 CB **OPEN** |
| 3 | OPEN 유지 | INIT 유지 | send 차단, "발행 보류"만 반복(블로킹 없음) |
| 4 | `docker start kafka` | INIT → **전부 SENT(4건)** | HALF_OPEN → CLOSED → 배출 |

## 로그 — CB 상태 전이 (핵심)

`send` 블로킹이 사라진 것이 타임스탬프 간격으로 드러난다.

**CLOSED (실제 send 시도, min-calls 채우는 중) — 11초 간격 = 10초 블로킹 + 1초 폴링**
```
14:10:06.313  WARN OutboxRelay : outbox 발행 실패 — 중단 후 재시도 대기 (id=2, eventType=OrderCreatedEvent): TimeoutException
14:10:17.323  WARN OutboxRelay : outbox 발행 실패 — 중단 후 재시도 대기 (id=2, eventType=OrderCreatedEvent): TimeoutException
```

**OPEN 전환 후 (send 차단) — 1초 간격 = 블로킹 없음**
```
14:10:18.332 DEBUG OutboxRelay : 카프카 서킷 OPEN — 발행 보류, 다음 폴링에서 재시도 (id=2)
14:10:19.340 DEBUG OutboxRelay : 카프카 서킷 OPEN — 발행 보류, 다음 폴링에서 재시도 (id=2)
14:10:20.347 DEBUG OutboxRelay : 카프카 서킷 OPEN — 발행 보류, 다음 폴링에서 재시도 (id=2)
...  (1초 간격으로 지속)
14:10:26.391 DEBUG OutboxRelay : 카프카 서킷 OPEN — 발행 보류, 다음 폴링에서 재시도 (id=2)
```

**HALF_OPEN 시험 (wait-duration 10s 후 소수 시험 발행 → 실패 → 재OPEN)**
```
14:10:37.405  WARN OutboxRelay : outbox 발행 실패 ... : TimeoutException    ← 14:10:26 + 10s 후 시험
14:10:48.415  WARN OutboxRelay : outbox 발행 실패 ... : TimeoutException
14:10:59.427  WARN OutboxRelay : outbox 발행 실패 ... : TimeoutException
14:11:00.432 DEBUG OutboxRelay : 카프카 서킷 OPEN — 발행 보류 ...            ← 시험 실패 후 다시 OPEN
```

카프카 복구 후에는 시험 발행이 성공해 CLOSED로 전이하고, 쌓인 INIT 3건이 배출되어 전부 SENT가 되었다.

## 진단 부록 — 처음에 CB가 안 열렸던 원인

최초 구현에서는 카프카를 죽여도 CB가 10분 넘게 OPEN으로 전환되지 않고, 매 폴링 10초씩 블로킹하며 실패만 반복했다. 원인 규명 과정:

1. **오진 1(프록시 탓)**: `@CircuitBreaker` 어노테이션이 인터페이스 구현(override) 메서드라 AOP가 안 걸린 것으로 추정하고 프로그래밍 방식(`CircuitBreakerRegistry.executeCallable`)으로 교체 → **여전히 안 열림.** 프록시 문제가 아니었음이 판명.
2. **config 실측**: CB 인스턴스의 실제 config를 로그로 찍어 `minCalls=3` 등 임계값은 정상 적용됨을 확인.
3. **진짜 원인**: `kafka-relay` config가 `configs.default`(PG feign 전용)의 `record-exceptions`를 암묵 상속 → 카프카의 `TimeoutException`을 **실패로 기록하지 않아** 실패율이 0으로 계산되어 CB가 CLOSED에 머물렀다.
4. **수정**: 전용 config에 `record-exceptions: [Exception]` + `ignore-exceptions: []`를 명시해 default 상속을 끊음. 어노테이션 방식으로 되돌려도 정상 OPEN 확인(프록시는 무관했음).

교훈: resilience4j에서 인스턴스 전용 config는 `record/ignore-exceptions`를 명시하지 않으면 `default`를 상속한다. 서로 다른 성격의 CB(외부 HTTP vs 내부 Kafka)를 한 앱에서 쓸 때는 예외 분류를 각 config에 못박아야 한다.

## 남은 과제 (Phase 5)

정량 rate 제어(배출 속도 튜닝), poison 이벤트 FAILED 격리, 컨슈머 DLQ, CB 파라미터의 부하 실험 기반 정밀화.
