# 조회 이벤트는 어디에 저장되고 있었나 — 랭킹 복구 원천을 설계하며

> 블로그 초안 (2026-07-17). Round 9 실시간 랭킹 작업 중 "이벤트가 유실되는 것 같다"는 의심에서 출발해, Outbox와 DLQ를 다시 이해하기까지의 기록.

## 결론부터

- "저장이 안 되는 것 같다"는 의심을 좇았더니, 정작 **이미 있던 자산**(Outbox, 행동 로그 테이블)을 재발견했다. 진짜 문제는 "저장 안 함"이 아니라 "**유실을 허용하며 저장함**"이었다.
- 유실을 막는 길은 둘이다. **① 같은 트랜잭션으로 묶어 원자적으로 저장**(Outbox 방식), **② 떼어내되 실패를 버리지 않고 재시도·격리**(DLQ 방식). 하나를 고르는 문제가 아니었다.
- Outbox와 DLQ는 **대립이 아니라 파이프라인의 서로 다른 구간**을 지키는 장치다. **발행 구간**은 Outbox 상태로, **소비 구간**은 Kafka DLQ로 이벤트를 버리지 않는다.
- 발행 실패를 Kafka DLQ로 격리하려던 건 착각이었다. **Kafka가 죽은 상황에서 Kafka 토픽에 격리할 수는 없다.**
- 저장소로 MongoDB·Elasticsearch를 떠올렸지만, **"없는 병목"을 위해 인프라를 늘리는 것**이었다. 특히 Elasticsearch는 write 특화가 아니다.
- 같은 "경계에서의 유실·보정" 문제는 **날짜가 바뀔 때의 랭킹 carry**에도 있었다. dual write에서 **23:50 스냅샷 + tail 병합**(grace period 패턴)으로 바꾸며, 무엇을 얻고 무엇을 대가로 남기는지를 기록했다.

---

## 1. 발단 — "이 이벤트, 저장은 되고 있나?"

랭킹은 Kafka 이벤트(상품 조회·좋아요·주문)를 소비해 만들어지는 파생 상태다. 그러다 문득 걸린 게 있었다. **랭킹이나 집계가 깨졌을 때, 뭘 보고 되살리지?** 파생 상태를 되살리려면 "무슨 이벤트가 있었나"를 담은 원천 소스가 남아 있어야 한다.

특히 상품 조회 이벤트가 마음에 걸렸다. 코드를 열어보니 조회는 이렇게 발행되고 있었다.

```kotlin
// 상품 상세 조회 사실 — 유실 허용이라 OutboxPublishable 을 구현하지 않는다(outbox 미적재, Kafka 직접 발행).
@Async
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
fun onProductViewed(event: ProductViewedEvent) {
    try {
        eventMessagePublisher.publish(USER_ACTION_EVENTS, event.productId.toString(), event)
    } catch (e: Exception) {
        logger.warn("ProductViewedEvent 발행 실패 — 유실 허용 (productId={}): {}", ...)
    }
}
```

발행이 실패하면 warn 로그만 남기고 버린다. 주석에도 "유실 허용"이라고 못 박혀 있다. 조회는 트래픽이 커서 의도적으로 그렇게 둔 것이다. 그런데 이걸 **복구 원천** 관점에서 다시 보면, 새고 있는 구멍이다.

## 2. Outbox를 다시 보다 — 이미 있었는데 조회만 빠져 있었다

"그럼 저장하자"로 가기 전에, 지금 뭐가 어떻게 저장되는지부터 확인했다. 그리고 **이미 Transactional Outbox가 구현돼 있었다.**

```kotlin
// BEFORE_COMMIT + 본 트랜잭션 참여 — 적재 실패 시 비즈니스도 함께 롤백(outbox 는 유실 불허).
@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
fun on(event: OutboxPublishable) {
    outboxEventRepository.save(OutboxEvent(...))
}
```

좋아요·주문은 `OutboxPublishable`을 구현해서, 비즈니스 write와 **같은 트랜잭션**으로 `outbox_event`에 INSERT된다. 릴레이가 폴링하며 Kafka로 발행하고, 성공할 때까지 재시도한다. 유실 불허다. 조회만 이 인터페이스를 안 구현해서 Outbox를 안 탔던 것이다.

게다가 행동 로그 테이블(`user_action_log`)은 조회·좋아요·주문·취소 4종을 이미 append하고 있었다. 다만 이것도 `@Async` + `catch 후 warn`이라 **append 실패 시 버린다.** 감사·분석용 원본으로는 충분하지만, 복구 원천(유실 불허)으로는 신뢰성이 부족했다.

**교훈: "저장 안 됨"이 아니라 "유실을 허용하며 저장함"이 진짜 문제였다.** 새 걸 만들기 전에 있는 걸 먼저 읽었어야 했다.

## 3. "유실 안 되게"의 두 가지 길

유실을 막는 방법은 원리부터 다른 둘이 있다.

**① 동기 — 같은 트랜잭션으로 묶기 (Outbox 방식).** 원천 저장을 비즈니스 트랜잭션 안에 넣으면, 비즈니스 write와 저장이 함께 커밋되거나 함께 롤백된다. "커밋됐는데 원천만 유실"이라는 틈 자체가 없다. 유실 방지를 **트랜잭션 원자성**이 보장한다. 대신 저장이 비즈니스 경로에 묶여(강결합), 조회처럼 `readOnly`인 트랜잭션이 쓰기 트랜잭션이 되고, 저장소가 느리면 비즈니스도 막힌다.

**② 비동기 — 떼어내되 버리지 않기 (DLQ 방식).** 원천 저장을 비즈니스에서 떼어내 consumer가 나중에 처리한다. 트랜잭션으로 못 묶으니, **실패해도 버리지 않는다**로 유실을 막는다. 몇 번 재시도하고, 그래도 안 되면 DLQ로 격리한다. 저장이 지연되지만(최종 정합), 비즈니스 경로는 저장소 장애와 격리되고 `readOnly`도 유지된다.

조회는 트래픽 특성상 비즈니스 경로에 쓰기를 얹기 부담스러웠다. 그래서 **②(비동기 DLQ)** 로 방향을 잡았다.

## 4. 착각의 정정 — Outbox와 DLQ는 대립이 아니다

여기서 한 번 꼬였다. 나는 "Outbox로 재시도하다 안 되면 DLQ로 간다"고 막연히 생각했는데, 위에서 둘을 "①이냐 ②냐"로 갈라놓으니 "둘 다 쓰는 게 아니었네?"로 혼란스러웠다.

정리해보니 **"Outbox"라는 단어가 두 가지를 가리키고 있었다.**

- **발행 Outbox** (commerce-api → Kafka): 이벤트를 Kafka에 확실히 넣는 producer 쪽 유실 방지.
- **원천 저장을 트랜잭션에 묶기**: 위 ①번에서 말한 저장 방식.

내가 "vs"로 대립시킨 건 ②(저장 지점의 방식) 얘기였고, 원래 상상했던 "Outbox로 재시도하다 DLQ"는 발행 Outbox 얘기였다. 이 둘은 **파이프라인의 다른 구간**이라 결합된다.

```
commerce-api
  [비즈니스 write + 발행 Outbox INSERT — 같은 트랜잭션(원자)]
        ↓  릴레이 폴링, Kafka 발행 성공까지 재시도   ← 발행 구간 (producer 유실 방지)
Kafka
        ↓  consumer 소비 → 원천 DB 저장
        저장 실패 → N회 재시도 → 소진 시 DLQ(+알림)   ← 소비 구간 (consumer 유실 방지)
원천 event store
```

두 안전장치가 각자 다른 구간을 지킨다. 견고한 파이프라인의 정상 모습이고, 내가 처음 상상한 그림이 사실 맞았던 것이다.

## 5. 발행 실패는 어디에 격리하나 — Kafka가 죽었는데 Kafka DLQ?

"발행 실패도 DLQ로"를 문자 그대로 구현하려다 함정을 만났다. **발행이 실패하는 상황은 대개 Kafka가 안 되는 상황**인데, DLQ는 Kafka 토픽이다. Kafka가 죽었는데 Kafka 토픽에 격리할 수는 없다.

그래서 발행 구간의 "안 되면"은 Kafka DLQ가 아니라 **`outbox_event`를 FAILED 상태로 마킹 + 알림**이어야 한다. 발행 구간에선 **Outbox 테이블 자체가 DLQ 역할**을 한다. Kafka DLQ가 의미 있는 건 오직 "Kafka는 살아있고 저장만 실패한" 소비 구간이다.

## 6. FAILED 상태의 진짜 값어치 — 유실 방지가 아니었다

FAILED 상태를 추가하려다 한 번 더 짚었다. 현재 릴레이는 발행 실패 시 그냥 다음 폴링에서 재시도한다. 즉 **실패분은 INIT 상태로 테이블에 남아 계속 재시도되므로, 유실은 이미 안 난다.** 상태를 추가한다고 유실 방지가 새로 생기는 게 아니었다.

FAILED의 진짜 실익은 다른 데 있었다.

- **Head-of-line blocking 해소.** 지금은 실패 시 릴레이가 멈춰서, 맨 앞 한 건이 계속 실패하면(역직렬화 불가 같은 독약 레코드) 그 뒤 전부가 영원히 못 나간다. N회 초과분을 FAILED로 빼면 막힌 뒤 레코드가 다시 흐른다.
- **관측·알림.** 계속 실패하는 걸 방치하지 않고 사람이 인지한다.

**교훈: 만들려는 장치의 효과를 정확히 말할 수 있어야 한다.** "유실 방지"라고 뭉뚱그렸다면, 이미 되고 있는 걸 중복 구현할 뻔했다.

## 7. 소비 구간의 함정 — 배치 리스너

소비 구간 DLQ는 Spring Kafka가 표준으로 지원한다. `DefaultErrorHandler` + `FixedBackOff`로 N회 재시도하고, 소진되면 `DeadLetterPublishingRecoverer`가 `<topic>-dlt`로 보낸다. 그리고 반가운 사실 — DLT 레코드에 이 헤더들이 자동으로 붙는다.

- `DLT_EXCEPTION_MESSAGE`, `DLT_EXCEPTION_STACKTRACE` — 무슨 예외로 실패했나 + 스택트레이스
- `DLT_ORIGINAL_TOPIC`, `_PARTITION`, `_OFFSET` — 어떤 대상이었나

"어떤 대상이 뭘 시도했는데 안 됐다 + 로그"를 직접 만들 필요가 없다.

단, 현재 consumer가 전부 **배치 리스너**(`isBatchListener = true`)라는 게 함정이었다. 두 제약이 딸려 온다.

1. 편한 `@RetryableTopic`(non-blocking retry)은 **배치 리스너에서 지원되지 않는다**(공식 문서 명시).
2. 배치에서 DLQ 하려면 실패 레코드를 담아 **`BatchListenerFailedException`을 던져야 한다.** 안 던지면 실패 하나에 배치 전체(최대 수천 건)가 통째로 재시도된다.

편의 기능이 구조와 안 맞는 경우다. 프레임워크 기능을 쓰기 전에 내 리스너가 record인지 batch인지부터 봤어야 했다.

## 8. 저장소 — MySQL이면 충분한데 왜 NoSQL을 떠올렸나

원천 데이터가 append-only에 "적재만 한다"는 성격이다 보니 자연스럽게 "write 특화된 MongoDB나 Elasticsearch가 낫지 않나" 생각이 들었다. 여기서 두 가지를 바로잡았다.

- **Elasticsearch는 write 특화가 아니다.** 검색·집계 특화다. 역색인 비용으로 write는 오히려 무겁고, near-real-time 색인이라 넣자마자 정확히 읽는 걸 보장하지 않으며, source of truth로 설계된 저장소가 아니다. 복구 원천으로는 안티패턴이다.
- **MongoDB의 write 우위는 샤딩까지 가는 규모에서 나온다.** 지금 조회 이벤트 볼륨이 MySQL INSERT를 못 버틴다는 실측 근거가 없다. append + 시간 파티셔닝(월별, 오래된 파티션 드롭)이면 MySQL이 로그성 데이터를 잘 감당한다.

새 저장소는 운영·정합성 관리 지점을 늘리고, 특히 복구 원천이면 그 저장소의 신뢰성 검증까지 떠안는다. 그래서 **우선 MySQL, 규모가 실측으로 넘어설 때 이전**으로 결론 냈다. 없는 병목을 위해 인프라를 늘리지 않는다.

## 9. 또 다른 경계 — 날짜가 바뀔 때 랭킹은 어디서 이어지나

유실·격리를 정리하고 나니 비슷한 성격의 경계가 하나 더 보였다. 랭킹은 **하루 단위 키**(`ranking:all:{yyyyMMdd}`)라 자정에 키가 바뀐다. 00:00:01의 새 키는 비어 있다 — 콜드 스타트다. 그래서 전일 점수의 일부(×0.1)를 새 날짜로 넘겨 웜 스타트를 만든다. 이게 carry다.

처음엔 **dual write**로 갔다. 이벤트가 들어올 때마다 오늘 키에 `+Δ`, 내일 키에 `+Δ×0.1`을 함께 쓴다. 자정이 되면 내일 키엔 이미 오늘의 10%가 쌓여 있다. 장점이 분명했다 — 누락되는 구간이 없고, 배치가 없으니 배치 실패도 없다.

그런데 두 가지가 걸렸다. 하나는 과제 Nice 요구 원문이 "23:50 스케줄러"였다는 것, 다른 하나는 **dual write가 이벤트마다 ZINCRBY를 2회씩 상시로 친다**는 것이다. Stage 1(MySQL) 측정에서 랭킹 소비 랙이 발산했던 원인 중 하나가 바로 "내일 행까지 upsert하는 2행 쓰기"였다. 웜 스타트라는 부수 효과를 위해 상시 쓰기를 2배로 무는 게 맞나?

그래서 스케줄러로 바꾸기로 했다. **23:50에 그날 누적을 한 번 스냅샷**해서 내일 키를 `ZUNIONSTORE 내일 = 오늘×0.1`로 seed한다. 이제 이벤트당 쓰기는 오늘 키 1회로 끝난다.

여기서 함정을 하나 발견했다. 스냅샷을 23:50에 찍으면 **23:50~00:00 사이에 들어온 이벤트의 10%가 내일 키에서 빠진다.** 처음엔 이걸 "발생시각 23:50~00:00인 이벤트"라고 생각했는데, 정확히는 아니었다. 컨슈머 랙 때문에 발생시각이 더 이른 이벤트도 스냅샷 뒤에 소비될 수 있다. 그러니 놓치는 건 "발생시각 창"이 아니라 **스냅샷(소비시각 23:50) 이후 오늘 키에 들어온 모든 증분**이다.

보정은 그래서 **벽시계 [23:50,00:00) 소비분**을 기준으로 잡았다. 그 구간 동안만 별도 `tail:{D}` ZSET에 같이 적재해두고, 00:00에 `ZUNIONSTORE 내일 += tail×0.1`로 병합한다. 스트림 처리에서 윈도우가 명목상 닫힌 뒤에도 늦게 온 이벤트를 유예 안에서 반영하는 개념 — Kafka Streams의 **grace period**, Flink의 **allowed lateness + side output**과 정확히 같은 모양이다. 늦게 온 것을 버리지 않고 옆으로 모았다가 반영한다.

물론 완전하진 않다. **오늘분을 00:00 이후에 깊게 지연 소비한 이벤트**는 여전히 내일 seed 10%에서 빠진다. 이건 남겨두기로 했다 — 랙에 유계이고, 어차피 seed용 10%지 실제 랭킹이 아니다. dual write는 이 잔여 갭이 0이었다. 그게 원래 채택 이유였고, 스케줄러로 바꾸며 감수한 대가다. 트레이드오프를 지우지 않고 문서에 남겼다.

마지막으로 배치가 생겼으니 멱등을 챙겨야 했다. 23:50 스냅샷은 `ZUNIONSTORE`가 destination을 덮어쓰므로 재실행해도 오늘 키에서 다시 계산된다 — 멱등이다. 하지만 00:00 병합은 **additive**라 두 번 돌면 tail이 두 번 더해진다. 그래서 `SET carry:merged:{D+1} NX`로 게이트를 걸어 한 번만 실행되게 했다. 스케줄러가 아예 안 돌면(노드 다운) 내일 키가 콜드로 시작하는데, 첫 이벤트가 곧 웜업하고 다음 tick이 캐치업한다. 배치 실패 알림은 앞서 DLQ에서 세운 `NotificationSender` 원칙을 그대로 쓴다.

돌아보면 carry도 결국 같은 질문이었다. "이 경계에서 뭘 잃을 수 있나, 잃은 걸 어디에 모았다가 어떻게 되돌리나." 유실·격리·보정이라는 뼈대는 발행/소비 구간과 다르지 않았다.

## 10. 정리 — 두 구간, 두 격리소

최종 그림은 이렇게 정리됐다.

| 구간 | 실패 지점 | 격리소 | 유실 방지 원리 |
|---|---|---|---|
| 발행 (api→Kafka) | Kafka 발행 실패 | `outbox_event` FAILED 상태 | 테이블에 남김 + 재시도 + 격리 |
| 소비 (streamer 저장) | 원천 DB 저장 실패 | Kafka `<topic>-dlt` | 재시도 + DLT 격리 + 알림 |

그리고 복구는 두 원천으로 나뉜다. **1차 복구는 Kafka replay**(retention 내 offset 되감기 재소비, 멱등 처리면 이중 가산 없음), **장기 복구는 DB event store**.

돌아보면 이 작업의 절반은 "새로 뭘 만들까"가 아니라 "이미 있는 게 뭘 하고 있나, 내가 만들려는 게 정확히 무슨 문제를 푸는가"를 계속 되묻는 일이었다. 유실 방지 같은 단어는 뭉뚱그리기 쉽고, 뭉뚱그리면 중복을 만들거나 엉뚱한 곳에 격리소를 둔다.

---

### 참고

- Spring for Apache Kafka — Handling Exceptions: https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html
- Spring for Apache Kafka — Non-Blocking Retries: https://docs.spring.io/spring-kafka/reference/retrytopic.html
- Redis — Leaderboard use case (per-window 키 · ZUNIONSTORE 집계): https://redis.io/docs/latest/develop/use-cases/leaderboard/
- Apache Kafka — Windows (grace period): https://kafka.apache.org/31/javadoc/org/apache/kafka/streams/kstream/Windows.html
- Apache Flink — Windows (Allowed Lateness · side output): https://nightlies.apache.org/flink/flink-docs-master/docs/dev/datastream/operators/windows/

*(설계 세부는 `docs/week9/06-event-store-recovery-dlq-design.html` 참조.)*
