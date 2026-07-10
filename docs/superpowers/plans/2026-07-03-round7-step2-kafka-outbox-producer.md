# Round 7 Step 2 (Plan B1) — Kafka Producer + Transactional Outbox (commerce-api)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** commerce-api의 Step 1 도메인 이벤트 중 시스템 간 전파가 필요한 것(좋아요, 결제성공)을 **Transactional Outbox**로 같은 트랜잭션에 기록하고, `@Scheduled` 릴레이가 Kafka로 at-least-once 발행한다. 조회(view)는 비즈니스 쓰기가 없으므로 best-effort **직접 발행**한다.

**Architecture:** Step 1의 `ApplicationEvent`가 단일 발행 지점. `@TransactionalEventListener(BEFORE_COMMIT)` 리스너가 좋아요/결제성공 이벤트를 `outbox_event` 행으로 직렬화(원본 tx에 참여 → 원자적). 릴레이가 PENDING 행을 `KafkaTemplate`로 발행 후 SENT. 조회 이벤트는 `@EventListener`가 Kafka로 직접 send(유실 허용).

**Tech Stack:** Kotlin 2.0.20, Spring Boot 3.4.4, spring-kafka(모듈 kafka `KafkaConfig`의 `KafkaTemplate<Any,Any>`), JPA(BaseEntity, ddl-auto), Jackson(supports:jackson `ObjectMapper`), Testcontainers(MySQL 기존 + Kafka 신규), JUnit5+AssertJ.

## Global Constraints

- 레이어: `domain/outbox`(엔티티+포트), `infrastructure/outbox`(JPA+어댑터+릴레이), `application/outbox`(캡처 리스너+메시지 팩토리+직접발행). 의존 방향 `interfaces→application→domain`, infrastructure는 port 구현.
- 엔티티는 `BaseEntity` 상속(auto id + created/updated/deletedAt), 생성자 인자 → `var x=x; protected set`, `init` 검증. `@Entity @Table(name=...)`.
- 토픽 상수: `catalog-events`(key=productId), `order-events`(key=orderId). 파티션 키 = String.
- 프로듀서: `acks=all`, `enable.idempotence=true`. payload는 JSON **문자열**로 저장/발행하므로 프로듀서 value-serializer는 **StringSerializer**(현재 JsonSerializer → 변경; commerce-api가 유일 프로듀서라 안전).
- 이벤트→토픽: `LikeCreatedEvent`→catalog(type=LIKE_ADDED), `LikeDeletedEvent`→catalog(LIKE_REMOVED), `PaymentSucceededEvent`→order(PAYMENT_SUCCEEDED), `ProductViewedEvent`→catalog(PRODUCT_VIEWED, **직접발행**).
- 멱등 키 `eventId`(UUID) — 캡처 시 생성, 소비자(B2)가 `event_handled`로 사용.
- streamer는 commerce-api에 의존하지 않는다(메시지는 JSON 계약; B2가 자체 DTO로 역직렬화).
- 기존 Step 1 핸들러/usecase 동작 불변(이벤트는 이미 발행됨 — 리스너만 추가).
- ktlint(≤130, 다중인자 각줄+trailing comma). 커밋 전 `./gradlew :apps:commerce-api:ktlintCheck -q`. 커밋 말미 `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.

---

### Task 1: Outbox 도메인 + 영속성 (엔티티·포트·JPA·어댑터)

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/outbox/OutboxEvent.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/outbox/OutboxStatus.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/outbox/OutboxEventRepository.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/outbox/OutboxEventJpaRepository.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/outbox/OutboxEventRepositoryImpl.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/outbox/OutboxEventTest.kt`

**Interfaces:**
- Produces: `OutboxEvent(topic, partitionKey, payload)` 생성 시 `eventId=UUID`, `status=PENDING`. `markSent()` → SENT + `sentAt`. `OutboxEventRepository { save(e); findTopPending(limit): List<OutboxEvent> }`.

- [ ] **Step 1: 실패 테스트 작성** — `OutboxEventTest.kt`

```kotlin
package com.loopers.domain.outbox

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class OutboxEventTest {
    @DisplayName("생성 시 eventId가 부여되고 상태는 PENDING이다.")
    @Test
    fun newOutboxEventIsPending() {
        val e = OutboxEvent(topic = "catalog-events", partitionKey = "10", payload = "{}")
        assertThat(e.status).isEqualTo(OutboxStatus.PENDING)
        assertThat(e.eventId).isNotBlank()
        assertThat(e.sentAt).isNull()
    }

    @DisplayName("markSent 하면 SENT로 전이하고 sentAt이 채워진다.")
    @Test
    fun markSentTransitions() {
        val e = OutboxEvent(topic = "catalog-events", partitionKey = "10", payload = "{}")
        e.markSent()
        assertThat(e.status).isEqualTo(OutboxStatus.SENT)
        assertThat(e.sentAt).isNotNull()
    }
}
```

- [ ] **Step 2: 실패 확인** — `./gradlew :apps:commerce-api:test --tests "com.loopers.domain.outbox.OutboxEventTest"` → FAIL(미해결).

- [ ] **Step 3: 구현**

`OutboxStatus.kt`:
```kotlin
package com.loopers.domain.outbox

enum class OutboxStatus { PENDING, SENT }
```

`OutboxEvent.kt` (payload는 길 수 있으니 `columnDefinition = "TEXT"`; eventId는 앱 생성 UUID):
```kotlin
package com.loopers.domain.outbox

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.ZonedDateTime
import java.util.UUID

@Entity
@Table(
    name = "outbox_events",
    uniqueConstraints = [UniqueConstraint(name = "uk_outbox_event_id", columnNames = ["event_id"])],
)
class OutboxEvent(
    topic: String,
    partitionKey: String,
    payload: String,
) : BaseEntity() {
    @Column(name = "event_id", nullable = false, updatable = false, length = 36)
    var eventId: String = UUID.randomUUID().toString()
        protected set

    @Column(name = "topic", nullable = false)
    var topic: String = topic
        protected set

    @Column(name = "partition_key", nullable = false)
    var partitionKey: String = partitionKey
        protected set

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    var payload: String = payload
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: OutboxStatus = OutboxStatus.PENDING
        protected set

    @Column(name = "sent_at")
    var sentAt: ZonedDateTime? = null
        protected set

    fun markSent() {
        status = OutboxStatus.SENT
        sentAt = ZonedDateTime.now()
    }
}
```

`OutboxEventRepository.kt` (port):
```kotlin
package com.loopers.domain.outbox

interface OutboxEventRepository {
    fun save(event: OutboxEvent): OutboxEvent
    fun findTopPending(limit: Int): List<OutboxEvent>
}
```

`OutboxEventJpaRepository.kt`:
```kotlin
package com.loopers.infrastructure.outbox

import com.loopers.domain.outbox.OutboxEvent
import com.loopers.domain.outbox.OutboxStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface OutboxEventJpaRepository : JpaRepository<OutboxEvent, Long> {
    fun findByStatusOrderByIdAsc(status: OutboxStatus, pageable: Pageable): List<OutboxEvent>
}
```

`OutboxEventRepositoryImpl.kt`:
```kotlin
package com.loopers.infrastructure.outbox

import com.loopers.domain.outbox.OutboxEvent
import com.loopers.domain.outbox.OutboxEventRepository
import com.loopers.domain.outbox.OutboxStatus
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component

@Component
class OutboxEventRepositoryImpl(
    private val jpa: OutboxEventJpaRepository,
) : OutboxEventRepository {
    override fun save(event: OutboxEvent): OutboxEvent = jpa.save(event)

    override fun findTopPending(limit: Int): List<OutboxEvent> =
        jpa.findByStatusOrderByIdAsc(OutboxStatus.PENDING, PageRequest.of(0, limit))
}
```

- [ ] **Step 4: 통과 확인** — 위 test 명령 → PASS.

- [ ] **Step 5: 커밋**
```bash
./gradlew :apps:commerce-api:ktlintCheck -q
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/outbox/ \
        apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/outbox/ \
        apps/commerce-api/src/test/kotlin/com/loopers/domain/outbox/OutboxEventTest.kt
git commit -m "feat: outbox_events entity + repository (R7-B1)"
```

---

### Task 2: Kafka 프로듀서 와이어링 + Outbox 캡처 리스너 (BEFORE_COMMIT)

**Files:**
- Modify: `apps/commerce-api/build.gradle.kts` (add `implementation(project(":modules:kafka"))`, `testImplementation(testFixtures(project(":modules:kafka")))`)
- Modify: `apps/commerce-api/src/main/resources/application.yml` (config.import에 `kafka.yml` 추가)
- Modify: `modules/kafka/src/main/resources/kafka.yml` (producer `acks: all`, `enable.idempotence: true`, value-serializer → StringSerializer)
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/outbox/KafkaTopics.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/outbox/OutboxMessageFactory.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/outbox/OutboxEventCaptureListener.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/application/outbox/OutboxMessageFactoryTest.kt`

**Interfaces:**
- Consumes(Step 1): `LikeCreatedEvent(productId)`, `LikeDeletedEvent(productId)`, `PaymentSucceededEvent(orderId, userId, items[Item(productId,quantity)])`.
- Produces: `OutboxMessageFactory.from(event): OutboxDraft(topic, partitionKey, payloadJson)` (순수, ObjectMapper 사용). `OutboxEventCaptureListener` — BEFORE_COMMIT에서 draft→`OutboxEvent` 저장.

- [ ] **Step 1: 실패 테스트 작성** — `OutboxMessageFactoryTest.kt` (payload JSON은 ObjectMapper로 파싱해 필드 검증 — 문자열 하드코딩 회피):

```kotlin
package com.loopers.application.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.like.LikeCreatedEvent
import com.loopers.domain.like.LikeDeletedEvent
import com.loopers.domain.order.OrderCreatedEvent
import com.loopers.domain.payment.PaymentSucceededEvent
import com.loopers.domain.outbox.KafkaTopics
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class OutboxMessageFactoryTest {
    private val om = ObjectMapper()
    private val factory = OutboxMessageFactory(om)

    @DisplayName("좋아요 생성 이벤트는 catalog-events, key=productId, type=LIKE_ADDED 로 매핑된다.")
    @Test
    fun mapsLikeCreated() {
        val draft = factory.from(LikeCreatedEvent(productId = 10L))!!
        assertThat(draft.topic).isEqualTo(KafkaTopics.CATALOG_EVENTS)
        assertThat(draft.partitionKey).isEqualTo("10")
        val node = om.readTree(draft.payload)
        assertThat(node["type"].asText()).isEqualTo("LIKE_ADDED")
        assertThat(node["productId"].asLong()).isEqualTo(10L)
        assertThat(node["eventId"].asText()).isNotBlank()
    }

    @DisplayName("좋아요 삭제 이벤트는 type=LIKE_REMOVED 로 매핑된다.")
    @Test
    fun mapsLikeDeleted() {
        val node = om.readTree(factory.from(LikeDeletedEvent(productId = 10L))!!.payload)
        assertThat(node["type"].asText()).isEqualTo("LIKE_REMOVED")
    }

    @DisplayName("결제성공 이벤트는 order-events, key=orderId, items 포함으로 매핑된다.")
    @Test
    fun mapsPaymentSucceeded() {
        val event = PaymentSucceededEvent(
            orderId = 1L, userId = 2L,
            items = listOf(PaymentSucceededEvent.Item(productId = 10L, quantity = 3)),
        )
        val draft = factory.from(event)!!
        assertThat(draft.topic).isEqualTo(KafkaTopics.ORDER_EVENTS)
        assertThat(draft.partitionKey).isEqualTo("1")
        val node = om.readTree(draft.payload)
        assertThat(node["type"].asText()).isEqualTo("PAYMENT_SUCCEEDED")
        assertThat(node["items"][0]["productId"].asLong()).isEqualTo(10L)
        assertThat(node["items"][0]["quantity"].asInt()).isEqualTo(3)
    }

    @DisplayName("아웃박스 대상이 아닌 이벤트(OrderCreated)는 null을 반환한다.")
    @Test
    fun ignoresNonOutboxEvent() {
        assertThat(factory.from(OrderCreatedEvent(orderId = 1L, userId = 2L, items = emptyList()))).isNull()
    }
}
```

- [ ] **Step 2: 실패 확인** — `./gradlew :apps:commerce-api:test --tests "com.loopers.application.outbox.OutboxMessageFactoryTest"` → FAIL.

- [ ] **Step 3: 구현**

`domain/outbox/KafkaTopics.kt`:
```kotlin
package com.loopers.domain.outbox

object KafkaTopics {
    const val CATALOG_EVENTS = "catalog-events"
    const val ORDER_EVENTS = "order-events"
}
```

`application/outbox/OutboxMessageFactory.kt` (payload는 LinkedHashMap→JSON; eventId UUID 생성):
```kotlin
package com.loopers.application.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.like.LikeCreatedEvent
import com.loopers.domain.like.LikeDeletedEvent
import com.loopers.domain.outbox.KafkaTopics
import com.loopers.domain.payment.PaymentSucceededEvent
import org.springframework.stereotype.Component
import java.time.ZonedDateTime
import java.util.UUID

data class OutboxDraft(val topic: String, val partitionKey: String, val payload: String)

@Component
class OutboxMessageFactory(
    private val objectMapper: ObjectMapper,
) {
    fun from(event: Any): OutboxDraft? =
        when (event) {
            is LikeCreatedEvent -> catalog("LIKE_ADDED", event.productId)
            is LikeDeletedEvent -> catalog("LIKE_REMOVED", event.productId)
            is PaymentSucceededEvent -> order(event)
            else -> null
        }

    private fun catalog(type: String, productId: Long): OutboxDraft {
        val payload = objectMapper.writeValueAsString(
            linkedMapOf(
                "eventId" to UUID.randomUUID().toString(),
                "type" to type,
                "productId" to productId,
                "occurredAt" to ZonedDateTime.now().toString(),
            ),
        )
        return OutboxDraft(KafkaTopics.CATALOG_EVENTS, productId.toString(), payload)
    }

    private fun order(event: PaymentSucceededEvent): OutboxDraft {
        val payload = objectMapper.writeValueAsString(
            linkedMapOf(
                "eventId" to UUID.randomUUID().toString(),
                "type" to "PAYMENT_SUCCEEDED",
                "orderId" to event.orderId,
                "userId" to event.userId,
                "items" to event.items.map { linkedMapOf("productId" to it.productId, "quantity" to it.quantity) },
                "occurredAt" to ZonedDateTime.now().toString(),
            ),
        )
        return OutboxDraft(KafkaTopics.ORDER_EVENTS, event.orderId.toString(), payload)
    }
}
```

`application/outbox/OutboxEventCaptureListener.kt` (BEFORE_COMMIT — 원본 tx 참여, 원자적):
```kotlin
package com.loopers.application.outbox

import com.loopers.domain.outbox.OutboxEvent
import com.loopers.domain.outbox.OutboxEventRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

// BEFORE_COMMIT: 원본 트랜잭션 안에서 outbox 행을 기록해 비즈니스 데이터와 원자적으로 커밋한다.
// (좋아요/결제성공 usecase 는 쓰기 트랜잭션이므로 flush 가 정상 동작한다. 조회(readOnly)는 대상 아님 — 직접 발행.)
@Component
class OutboxEventCaptureListener(
    private val factory: OutboxMessageFactory,
    private val outboxRepository: OutboxEventRepository,
) {
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun capture(event: Any) {
        val draft = factory.from(event) ?: return
        outboxRepository.save(
            OutboxEvent(topic = draft.topic, partitionKey = draft.partitionKey, payload = draft.payload),
        )
    }
}
```

`build.gradle.kts` — deps 추가:
```kotlin
    implementation(project(":modules:kafka"))
    // ...
    testImplementation(testFixtures(project(":modules:kafka")))
```

`application.yml` — `config.import`에 `kafka.yml` 추가:
```yaml
  config:
    import:
      - jpa.yml
      - redis.yml
      - kafka.yml
      - logging.yml
      - monitoring.yml
```

`modules/kafka/src/main/resources/kafka.yml` — producer 블록:
```yaml
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
      acks: all
      retries: 3
      properties:
        enable.idempotence: true
```

- [ ] **Step 4: 통과 확인** — factory test PASS. (캡처 리스너의 tx 원자성은 Task 3 통합테스트에서 검증)

- [ ] **Step 5: 커밋**
```bash
./gradlew :apps:commerce-api:ktlintCheck -q
git add apps/commerce-api/build.gradle.kts apps/commerce-api/src/main/resources/application.yml \
        modules/kafka/src/main/resources/kafka.yml \
        apps/commerce-api/src/main/kotlin/com/loopers/domain/outbox/KafkaTopics.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/application/outbox/ \
        apps/commerce-api/src/test/kotlin/com/loopers/application/outbox/OutboxMessageFactoryTest.kt
git commit -m "feat: kafka producer wiring + outbox capture listener (R7-B1)"
```

---

### Task 3: Kafka Testcontainers + OutboxRelay(@Scheduled) — end-to-end 발행

**Files:**
- Create: `modules/kafka/src/testFixtures/kotlin/com/loopers/testcontainers/KafkaTestContainersConfig.kt`
- Modify: `modules/kafka/build.gradle.kts` (testFixtures에 testcontainers kafka가 이미 선언됨 — 확인만; 필요시 `testFixturesApi`로 노출)
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/outbox/OutboxRelay.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/CommerceApiApplication.kt` (`@EnableScheduling`)
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/outbox/OutboxRelayIntegrationTest.kt`

**Interfaces:**
- Consumes: `OutboxEventRepository`, `KafkaTemplate<Any, Any>`(모듈 kafka 빈).
- Produces: `OutboxRelay.relayOnce(): Int` (PENDING 행 발행 후 SENT 처리, 발행 수 반환). `@Scheduled(fixedDelay=1000)`가 `relayOnce()` 호출.

- [ ] **Step 1: 실패 테스트 작성** — `OutboxRelayIntegrationTest.kt` (MySQL + Kafka 컨테이너; 테스트용 KafkaConsumer로 수신 검증):

```kotlin
package com.loopers.infrastructure.outbox

import com.loopers.domain.outbox.KafkaTopics
import com.loopers.domain.outbox.OutboxEvent
import com.loopers.domain.outbox.OutboxEventRepository
import com.loopers.domain.outbox.OutboxStatus
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import java.time.Duration
import java.util.UUID

@SpringBootTest
class OutboxRelayIntegrationTest {
    @Autowired lateinit var outboxRepository: OutboxEventRepository
    @Autowired lateinit var outboxRelay: OutboxRelay
    @Autowired lateinit var databaseCleanUp: com.loopers.utils.DatabaseCleanUp

    @Value("\${spring.kafka.bootstrap-servers}") lateinit var bootstrap: String

    @AfterEach fun tearDown() = databaseCleanUp.truncateAllTables()

    @DisplayName("PENDING outbox 행을 relayOnce 하면 Kafka로 발행되고 SENT로 전이한다.")
    @Test
    fun relayPublishesAndMarksSent() {
        // arrange
        val saved = outboxRepository.save(
            OutboxEvent(topic = KafkaTopics.CATALOG_EVENTS, partitionKey = "10", payload = "{\"type\":\"LIKE_ADDED\",\"productId\":10}"),
        )
        consumer(KafkaTopics.CATALOG_EVENTS).use { c ->
            // act
            val count = outboxRelay.relayOnce()

            // assert: published
            assertThat(count).isEqualTo(1)
            val records = c.poll(Duration.ofSeconds(10))
            assertThat(records.count()).isEqualTo(1)
            val rec = records.iterator().next()
            assertThat(rec.key()).isEqualTo("10")
            assertThat(rec.value()).contains("LIKE_ADDED")
            // assert: marked SENT
            assertThat(outboxRepository.findTopPending(10)).isEmpty()
        }
    }

    private fun consumer(topic: String): KafkaConsumer<String, String> {
        val props = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrap,
            ConsumerConfig.GROUP_ID_CONFIG to "test-${UUID.randomUUID()}",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
        )
        return KafkaConsumer<String, String>(props).apply { subscribe(listOf(topic)) }
    }
}
```
> 주의: `auto.create.topics.enable=false`(kafka.yml)이므로 토픽이 없으면 발행/구독 실패할 수 있다. 테스트에서 컨테이너의 `auto.create.topics.enable`를 켜거나(권장, 아래 config), `KafkaTestContainersConfig`가 토픽을 미리 생성하도록 한다. 구현 시 컨테이너 env로 `KAFKA_AUTO_CREATE_TOPICS_ENABLE=true` 설정.

- [ ] **Step 2: 실패 확인** — `./gradlew :apps:commerce-api:test --tests "com.loopers.infrastructure.outbox.OutboxRelayIntegrationTest"` → FAIL(미해결/미발행).

- [ ] **Step 3: 구현**

`KafkaTestContainersConfig.kt` (MySqlTestContainersConfig 미러; `spring.kafka.bootstrap-servers` 시스템 프로퍼티 주입):
```kotlin
package com.loopers.testcontainers

import org.springframework.context.annotation.Configuration
import org.testcontainers.kafka.ConfluentKafkaContainer
import org.testcontainers.utility.DockerImageName

@Configuration
class KafkaTestContainersConfig {
    companion object {
        private val kafkaContainer: ConfluentKafkaContainer =
            ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"))
                .apply {
                    withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true")
                    start()
                }

        init {
            System.setProperty("spring.kafka.bootstrap-servers", kafkaContainer.bootstrapServers)
        }
    }
}
```
> `org.testcontainers:kafka`의 컨테이너 클래스명이 버전에 따라 `KafkaContainer`(deprecated) 또는 `ConfluentKafkaContainer`다. 구현자는 실제 testcontainers 버전에서 사용 가능한 클래스로 맞춘다(둘 다 `bootstrapServers` 제공). 이미지 태그도 해당 버전 문서 기준.

`OutboxRelay.kt` (KafkaTemplate<Any,Any>는 모듈 kafka 빈; String key/value 전송):
```kotlin
package com.loopers.infrastructure.outbox

import com.loopers.domain.outbox.OutboxEventRepository
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OutboxRelay(
    private val outboxRepository: OutboxEventRepository,
    private val kafkaTemplate: KafkaTemplate<Any, Any>,
) {
    private val log = LoggerFactory.getLogger(OutboxRelay::class.java)

    companion object {
        private const val BATCH_SIZE = 100
    }

    @Scheduled(fixedDelay = 1000)
    fun relay() {
        runCatching { relayOnce() }
            .onFailure { log.warn("Outbox relay failed", it) }
    }

    // PENDING 행을 순서대로 발행하고 SENT 전이. 발행 실패 시 PENDING 유지 → 다음 주기 재시도(at-least-once).
    @Transactional
    fun relayOnce(): Int {
        val pending = outboxRepository.findTopPending(BATCH_SIZE)
        var sent = 0
        for (e in pending) {
            // acks=all + idempotence=true 프로듀서. 동기 대기로 발행 확인 후 SENT.
            kafkaTemplate.send(e.topic, e.partitionKey, e.payload).get()
            e.markSent()
            outboxRepository.save(e)
            sent++
        }
        return sent
    }
}
```
> `KafkaTemplate<Any,Any>.send(topic, key, value)` — value-serializer=StringSerializer이므로 String payload가 그대로 전송된다. `.get()`은 블로킹(단일 스케줄러 스레드, 중간 규모 OK). ponytail: 동기 전송, 처리량 필요 시 비동기 배치로 업그레이드.

`CommerceApiApplication.kt` — `@EnableScheduling` 추가(클래스 어노테이션).

- [ ] **Step 4: 통과 확인** — Docker 필요(MySQL+Kafka 컨테이너). test PASS.

- [ ] **Step 5: 커밋**
```bash
./gradlew :apps:commerce-api:ktlintCheck -q
git add modules/kafka/src/testFixtures/ modules/kafka/build.gradle.kts \
        apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/outbox/OutboxRelay.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/CommerceApiApplication.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/outbox/OutboxRelayIntegrationTest.kt
git commit -m "feat: outbox relay + kafka testcontainers, end-to-end publish (R7-B1)"
```

---

### Task 4: 좋아요/결제 → Outbox 원자적 캡처 통합테스트 + 조회 직접발행

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/outbox/ProductViewKafkaPublisher.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/application/outbox/OutboxCaptureIntegrationTest.kt`

**Interfaces:**
- `ProductViewKafkaPublisher` — `@EventListener`(비-tx, best-effort) on `ProductViewedEvent` → `kafkaTemplate.send(catalog-events, productId, payload)`. 실패는 로깅만(유실 허용).

- [ ] **Step 1: 실패 테스트 작성** — `OutboxCaptureIntegrationTest.kt` (좋아요 usecase 실행 → 같은 tx로 outbox 행 생성 확인; 롤백 시 미생성):

```kotlin
package com.loopers.application.outbox

import com.loopers.application.like.usecase.LikeProductCommand
import com.loopers.application.like.usecase.LikeProductUsecase
import com.loopers.domain.outbox.KafkaTopics
import com.loopers.domain.outbox.OutboxEventRepository
// (user/product fixture 준비는 기존 통합테스트 픽스처 패턴 재사용)
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class OutboxCaptureIntegrationTest {
    @Autowired lateinit var likeProductUsecase: LikeProductUsecase
    @Autowired lateinit var outboxRepository: OutboxEventRepository
    @Autowired lateinit var databaseCleanUp: com.loopers.utils.DatabaseCleanUp
    // + user/product 저장용 repository 주입 (기존 통합테스트가 쓰는 것과 동일하게)

    @AfterEach fun tearDown() = databaseCleanUp.truncateAllTables()

    @DisplayName("좋아요 등록이 커밋되면 같은 트랜잭션으로 outbox 행이 생성된다.")
    @Test
    fun likeCreatesOutboxRow() {
        // arrange: 활성 유저/상품 저장 (기존 통합테스트 arrange 재사용)
        // val (loginId, password, productId) = ...

        // act
        likeProductUsecase.execute(LikeProductCommand(loginId, password, productId))

        // assert
        val pending = outboxRepository.findTopPending(10)
        assertThat(pending).hasSize(1)
        assertThat(pending.first().topic).isEqualTo(KafkaTopics.CATALOG_EVENTS)
        assertThat(pending.first().partitionKey).isEqualTo(productId.toString())
    }
}
```
> 구현자: 유저/상품 셋업은 이 모듈의 기존 통합테스트(예: `OrderCouponIntegrationTest`/`SyncPaymentResultUsecaseIntegrationTest`)가 쓰는 fixture/repository 주입 방식을 그대로 따른다. 활성 상품·유저가 있어야 `LikeProductUsecase`가 통과한다.

- [ ] **Step 2: 실패 확인** — FAIL(ProductViewKafkaPublisher 미존재는 이 테스트와 무관하나, 좋아요 캡처가 안 되면 pending 0). 우선 캡처 리스너(Task 2)만으로 통과해야 하므로, 이 테스트가 RED면 캡처 배선 문제를 잡는다.

- [ ] **Step 3: 구현** — 조회 직접발행 퍼블리셔

`application/outbox/ProductViewKafkaPublisher.kt`:
```kotlin
package com.loopers.application.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.outbox.KafkaTopics
import com.loopers.domain.product.ProductViewedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.time.ZonedDateTime
import java.util.UUID

// 조회는 비즈니스 쓰기가 없어 아웃박스(원자성) 대상이 아니다. best-effort 직접 발행(유실 허용).
@Component
class ProductViewKafkaPublisher(
    private val kafkaTemplate: KafkaTemplate<Any, Any>,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(ProductViewKafkaPublisher::class.java)

    @Async
    @EventListener
    fun onProductViewed(event: ProductViewedEvent) {
        val payload = objectMapper.writeValueAsString(
            linkedMapOf(
                "eventId" to UUID.randomUUID().toString(),
                "type" to "PRODUCT_VIEWED",
                "productId" to event.productId,
                "occurredAt" to ZonedDateTime.now().toString(),
            ),
        )
        runCatching { kafkaTemplate.send(KafkaTopics.CATALOG_EVENTS, event.productId.toString(), payload) }
            .onFailure { log.warn("Failed to publish ProductViewedEvent. productId={}", event.productId, it) }
    }
}
```
> `@EventListener`(비-tx): 조회 usecase는 readOnly라 아웃박스에 못 쓰지만, 이벤트 발행 자체는 정상. AFTER_COMMIT이 아닌 즉시 @Async 발행(조회는 롤백 드묾, 유실 허용). Step 1 `UserActionLogEventHandler`의 AFTER_COMMIT 로깅과 별개.

- [ ] **Step 4: 통과 확인** — 좋아요 캡처 통합테스트 PASS. (조회 직접발행은 Task 3의 컨테이너 인프라로 통합 확인 가능하나, 본 태스크에선 퍼블리셔 존재+컴파일+좋아요 캡처 검증까지.)

- [ ] **Step 5: 전체 회귀 + 커밋**
```bash
./gradlew :apps:commerce-api:test -q
./gradlew :apps:commerce-api:ktlintCheck -q
git add apps/commerce-api/src/main/kotlin/com/loopers/application/outbox/ProductViewKafkaPublisher.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/application/outbox/OutboxCaptureIntegrationTest.kt
git commit -m "feat: product-view direct publish + outbox capture integration test (R7-B1)"
```

---

## Step B1 완료 기준 (DoD)

- `outbox_events` 테이블 + 좋아요/결제성공 이벤트가 **같은 tx**로 outbox 행 생성(원자성).
- `@Scheduled` 릴레이가 PENDING → Kafka 발행(acks=all, idempotence) → SENT. Kafka Testcontainers로 왕복 검증.
- 조회는 best-effort 직접 발행.
- `./gradlew :apps:commerce-api:test`, `:apps:commerce-api:ktlintCheck`, 그리고 `:apps:commerce-batch:test`(SyncPaymentResultUsecase 재사용 — kafka.yml producer 변경/@EnableScheduling 영향 없음 확인) 통과.

## Self-Review (spec 대비)

- **Transactional Outbox(spec §7)** → Task 1(테이블)+Task 2(BEFORE_COMMIT 캡처)+Task 3(릴레이). ✅
- **acks=all, idempotence(spec §7 Producer 필수)** → Task 2 kafka.yml. ✅
- **PartitionKey 순서(productId/orderId)** → Task 2 factory(key=productId/orderId). ✅
- **토픽 catalog-events/order-events(spec 토픽설계)** → KafkaTopics. ✅ (coupon-issue-requests는 Step 3/Plan C.)
- **view_count 소스(사용자 결정: 직접발행)** → Task 4 ProductViewKafkaPublisher. ✅
- **Kafka Testcontainers(spec §7 gap)** → Task 3. ✅
- 미커버(의도적): Consumer/product_metrics/event_handled = Plan B2. 선착순 = Plan C.
- 타입 일관성: `OutboxDraft(topic,partitionKey,payload)` 생산(Task2)=소비(Task3 relay via OutboxEvent). `PaymentSucceededEvent.Item(productId,quantity)`(Step1) = factory 소비(Task2).

## 리스크 / 주의

- **kafka.yml consumer `value-serializer`(line 21)** 는 오타로 보이나(→ value-deserializer) 본 플랜의 프로듀서 경로와 무관 — B2에서 소비자 배선 시 점검.
- **BEFORE_COMMIT flush**: 좋아요/결제 usecase는 쓰기 tx라 outbox insert가 커밋 전 flush됨. readOnly usecase(조회)는 대상 아님(직접발행으로 회피).
- **auto.create.topics.enable=false**(운영) — 테스트 컨테이너만 true. 운영 토픽은 별도 생성 필요(운영 반영 시 flag).
- **@EnableScheduling** 이 commerce-api 전역 스케줄러 활성화 — 기존 스케줄러 없음(신규). 릴레이 1s 주기. (스케줄러가 테스트 컨텍스트에서 돌며 outbox를 발행할 수 있으니, 통합테스트는 `relayOnce()` 직접 호출로 결정적 검증. 릴레이 자동주기와 경합하면 fixedDelay를 크게 두는 프로퍼티(`outbox.relay.fixed-delay`)로 테스트서 비활성화하는 것도 고려.)
- **KafkaTestContainersConfig always-on 비용**: MySQL/Redis 설정처럼 `com.loopers.testcontainers` 아래 `@Configuration`이면 **모든** `@SpringBootTest`가 Kafka 컨테이너를 기동 → 결제/주문 등 Kafka 불필요 통합테스트도 느려진다(레포 기존 패턴과 일관되나 비용 있음). 대안: config를 자동스캔되지 않게 두고 Kafka 필요한 테스트만 `@Import(KafkaTestContainersConfig)` 하는 opt-in. 구현자는 먼저 always-on(패턴 일관)으로 가되, 전체 스위트 시간이 크게 늘면 opt-in으로 전환하고 이유를 리뷰에 남긴다.
- **릴레이 tx 내 Kafka 동기 전송**: `relayOnce()`가 `@Transactional` 안에서 `send().get()` 블로킹 — DB 커넥션을 Kafka I/O 동안 점유. 과제 규모 OK, 소비자 멱등(event_handled)으로 중복 무해. 처리량/커넥션 이슈 시 send를 tx 밖으로 빼고 markSent만 짧은 tx로.
- Docker 필요(Task 3/4 통합테스트: MySQL+Kafka 컨테이너).

## 다음
B1 구현·검증 후 **Plan B2**(commerce-streamer Consumer: product_metrics upsert + event_handled 멱등 + manual ack) 작성.
