# Round 7 Step 2 (Plan B2) — Kafka Consumer + product_metrics (commerce-streamer)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`) syntax.

**Goal:** commerce-streamer가 `catalog-events`/`order-events`를 소비해 `product_metrics`(좋아요/판매/조회 수)에 **멱등하게** upsert 집계한다. `event_handled(event_id PK)`로 중복 메시지를 무해화하고, manual ack로 처리 완료 후에만 커밋한다.

**Architecture:** B1 프로듀서가 발행한 JSON 메시지를 streamer가 **자체 DTO**로 역직렬화(commerce-api 비의존). `@KafkaListener`(모듈 kafka `BATCH_LISTENER` 팩토리, manual ack) → `event_handled` 멱등 체크/기록 → `product_metrics` 원자적 upsert(`INSERT … ON DUPLICATE KEY UPDATE`) → ack. 파티션 키(productId/orderId)로 동일 상품/주문 순서 보장, sales의 상품-교차 경합은 DB 원자적 증분으로 처리.

**Tech Stack:** Kotlin 2.0.20, Spring Boot 3.4.4, spring-kafka(`KafkaConfig.BATCH_LISTENER`, ByteArray value), JPA(공유 MySQL, ddl-auto), Jackson `ObjectMapper`, Testcontainers(MySQL + Kafka), JUnit5+AssertJ.

## Global Constraints

- 레이어: `domain/metrics`(엔티티+포트), `infrastructure/metrics`(JPA+어댑터), `application/metrics`(DTO+서비스), `interfaces/consumer`(@KafkaListener). streamer는 **commerce-api에 의존하지 않는다** — 메시지는 JSON 계약으로만 소통.
- **메시지 계약(B1이 발행):**
  - `catalog-events`(key=productId): `{"eventId":str,"type":"LIKE_ADDED"|"LIKE_REMOVED"|"PRODUCT_VIEWED","productId":long,"occurredAt":str}`
  - `order-events`(key=orderId): `{"eventId":str,"type":"PAYMENT_SUCCEEDED","orderId":long,"userId":long,"items":[{"productId":long,"quantity":int}],"occurredAt":str}`
  - `eventId`는 멱등 키.
- 집계 규칙: LIKE_ADDED → like+1, LIKE_REMOVED → like-1, PRODUCT_VIEWED → view+1, PAYMENT_SUCCEEDED → 각 item sales += quantity.
- `product_metrics`/`event_handled`는 파생/기술 테이블 → **BaseEntity 미상속**(product_id / event_id를 @Id 로).
- 멱등: `event_handled(event_id PK)` 존재 시 skip, 없으면 기록 + 집계, 한 트랜잭션. 중복 메시지 재수신에도 최종 결과 1회 반영.
- manual Ack: batch listener에서 전체 레코드 처리 후 `acknowledgment.acknowledge()`.
- ktlint(≤130). 커밋 말미 `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.

---

### Task 1: product_metrics + event_handled 엔티티/리포지토리 + 멱등 upsert 서비스

**Files:**
- Create: `apps/commerce-streamer/src/main/kotlin/com/loopers/domain/metrics/ProductMetric.kt`
- Create: `apps/commerce-streamer/src/main/kotlin/com/loopers/domain/metrics/EventHandled.kt`
- Create: `apps/commerce-streamer/src/main/kotlin/com/loopers/domain/metrics/ProductMetricRepository.kt`
- Create: `apps/commerce-streamer/src/main/kotlin/com/loopers/domain/metrics/EventHandledRepository.kt`
- Create: `apps/commerce-streamer/src/main/kotlin/com/loopers/infrastructure/metrics/ProductMetricJpaRepository.kt`
- Create: `apps/commerce-streamer/src/main/kotlin/com/loopers/infrastructure/metrics/ProductMetricRepositoryImpl.kt`
- Create: `apps/commerce-streamer/src/main/kotlin/com/loopers/infrastructure/metrics/EventHandledJpaRepository.kt`
- Create: `apps/commerce-streamer/src/main/kotlin/com/loopers/infrastructure/metrics/EventHandledRepositoryImpl.kt`
- Create: `apps/commerce-streamer/src/main/kotlin/com/loopers/application/metrics/ProductMetricsService.kt`
- Modify: `apps/commerce-streamer/build.gradle.kts` (add `testImplementation(testFixtures(project(":modules:kafka")))`)
- Test: `apps/commerce-streamer/src/test/kotlin/com/loopers/application/metrics/ProductMetricsServiceIntegrationTest.kt`

**Interfaces:**
- `ProductMetricRepository.upsertDelta(productId, likeDelta, salesDelta, viewDelta)` (원자적 INSERT..ON DUPLICATE KEY UPDATE), `findByProductId(productId): ProductMetric?`.
- `EventHandledRepository.markHandled(eventId): Boolean` — 신규면 true(기록), 이미 있으면 false.
- `ProductMetricsService.applyOnce(eventId, productDeltas: List<MetricDelta>)` — eventId 신규일 때만 각 delta upsert. `MetricDelta(productId, like, sales, view)`.

- [ ] **Step 1: 실패 테스트 작성** — `ProductMetricsServiceIntegrationTest.kt` (MySQL 컨테이너; 첫 적용 반영 + 중복 eventId skip):

```kotlin
package com.loopers.application.metrics

import com.loopers.domain.metrics.ProductMetricRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ProductMetricsServiceIntegrationTest {
    @Autowired lateinit var service: ProductMetricsService
    @Autowired lateinit var metricRepository: ProductMetricRepository
    @Autowired lateinit var databaseCleanUp: DatabaseCleanUp

    @AfterEach fun tearDown() = databaseCleanUp.truncateAllTables()

    @DisplayName("신규 eventId면 좋아요 수를 upsert(신규 행 생성)한다.")
    @Test
    fun appliesLikeOnce() {
        service.applyOnce("evt-1", listOf(MetricDelta(productId = 10L, like = 1)))
        assertThat(metricRepository.findByProductId(10L)?.likeCount).isEqualTo(1L)
    }

    @DisplayName("같은 eventId를 두 번 적용해도 결과는 1회만 반영된다(멱등).")
    @Test
    fun idempotentOnDuplicateEventId() {
        service.applyOnce("evt-2", listOf(MetricDelta(productId = 10L, like = 1)))
        service.applyOnce("evt-2", listOf(MetricDelta(productId = 10L, like = 1)))
        assertThat(metricRepository.findByProductId(10L)?.likeCount).isEqualTo(1L)
    }

    @DisplayName("판매 이벤트는 상품별 sales_count에 수량만큼 누적된다.")
    @Test
    fun accumulatesSales() {
        service.applyOnce("evt-3", listOf(MetricDelta(productId = 10L, sales = 3)))
        service.applyOnce("evt-4", listOf(MetricDelta(productId = 10L, sales = 2)))
        assertThat(metricRepository.findByProductId(10L)?.salesCount).isEqualTo(5L)
    }
}
```

- [ ] **Step 2: 실패 확인** — `./gradlew :apps:commerce-streamer:test --tests "com.loopers.application.metrics.ProductMetricsServiceIntegrationTest"` → FAIL(미해결).

- [ ] **Step 3: 구현**

`domain/metrics/ProductMetric.kt`:
```kotlin
package com.loopers.domain.metrics

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "product_metrics")
class ProductMetric(
    @Id
    @Column(name = "product_id")
    val productId: Long,
    @Column(name = "like_count", nullable = false)
    var likeCount: Long = 0,
    @Column(name = "sales_count", nullable = false)
    var salesCount: Long = 0,
    @Column(name = "view_count", nullable = false)
    var viewCount: Long = 0,
)
```

`domain/metrics/EventHandled.kt`:
```kotlin
package com.loopers.domain.metrics

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "event_handled")
class EventHandled(
    @Id
    @Column(name = "event_id", length = 36)
    val eventId: String,
    @Column(name = "handled_at", nullable = false)
    val handledAt: ZonedDateTime = ZonedDateTime.now(),
)
```

`domain/metrics/ProductMetricRepository.kt`:
```kotlin
package com.loopers.domain.metrics

interface ProductMetricRepository {
    fun upsertDelta(productId: Long, likeDelta: Long, salesDelta: Long, viewDelta: Long)
    fun findByProductId(productId: Long): ProductMetric?
}
```

`domain/metrics/EventHandledRepository.kt`:
```kotlin
package com.loopers.domain.metrics

interface EventHandledRepository {
    fun markHandled(eventId: String): Boolean
}
```

`infrastructure/metrics/ProductMetricJpaRepository.kt` (원자적 upsert — 동시 다른 이벤트의 같은 상품 경합 안전):
```kotlin
package com.loopers.infrastructure.metrics

import com.loopers.domain.metrics.ProductMetric
import jakarta.transaction.Transactional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ProductMetricJpaRepository : JpaRepository<ProductMetric, Long> {
    @Transactional
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO product_metrics (product_id, like_count, sales_count, view_count)
            VALUES (:productId, :likeDelta, :salesDelta, :viewDelta)
            ON DUPLICATE KEY UPDATE
                like_count = like_count + :likeDelta,
                sales_count = sales_count + :salesDelta,
                view_count = view_count + :viewDelta
        """,
    )
    fun upsertDelta(
        @Param("productId") productId: Long,
        @Param("likeDelta") likeDelta: Long,
        @Param("salesDelta") salesDelta: Long,
        @Param("viewDelta") viewDelta: Long,
    )
}
```

`infrastructure/metrics/ProductMetricRepositoryImpl.kt`:
```kotlin
package com.loopers.infrastructure.metrics

import com.loopers.domain.metrics.ProductMetric
import com.loopers.domain.metrics.ProductMetricRepository
import org.springframework.stereotype.Component

@Component
class ProductMetricRepositoryImpl(
    private val jpa: ProductMetricJpaRepository,
) : ProductMetricRepository {
    override fun upsertDelta(productId: Long, likeDelta: Long, salesDelta: Long, viewDelta: Long) =
        jpa.upsertDelta(productId, likeDelta, salesDelta, viewDelta)

    override fun findByProductId(productId: Long): ProductMetric? = jpa.findById(productId).orElse(null)
}
```

`infrastructure/metrics/EventHandledJpaRepository.kt`:
```kotlin
package com.loopers.infrastructure.metrics

import com.loopers.domain.metrics.EventHandled
import org.springframework.data.jpa.repository.JpaRepository

interface EventHandledJpaRepository : JpaRepository<EventHandled, String>
```

`infrastructure/metrics/EventHandledRepositoryImpl.kt` (신규 기록 성공=true, 중복=false):
```kotlin
package com.loopers.infrastructure.metrics

import com.loopers.domain.metrics.EventHandled
import com.loopers.domain.metrics.EventHandledRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component

@Component
class EventHandledRepositoryImpl(
    private val jpa: EventHandledJpaRepository,
) : EventHandledRepository {
    override fun markHandled(eventId: String): Boolean {
        if (jpa.existsById(eventId)) return false
        return try {
            jpa.save(EventHandled(eventId))
            true
        } catch (e: DataIntegrityViolationException) {
            false // 동시 중복 삽입 — 이미 처리됨
        }
    }
}
```

`application/metrics/ProductMetricsService.kt`:
```kotlin
package com.loopers.application.metrics

import com.loopers.domain.metrics.EventHandledRepository
import com.loopers.domain.metrics.ProductMetricRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class MetricDelta(
    val productId: Long,
    val like: Long = 0,
    val sales: Long = 0,
    val view: Long = 0,
)

@Service
class ProductMetricsService(
    private val eventHandledRepository: EventHandledRepository,
    private val productMetricRepository: ProductMetricRepository,
) {
    // eventId 신규일 때만 delta 적용 — event_handled 기록과 집계가 한 트랜잭션(멱등).
    @Transactional
    fun applyOnce(eventId: String, deltas: List<MetricDelta>) {
        if (!eventHandledRepository.markHandled(eventId)) return
        deltas.forEach {
            productMetricRepository.upsertDelta(it.productId, it.like, it.sales, it.view)
        }
    }
}
```

`build.gradle.kts` — test-fixtures에 kafka 추가:
```kotlin
    testImplementation(testFixtures(project(":modules:kafka")))
```

- [ ] **Step 4: 통과 확인** — test PASS (Docker: MySQL).

- [ ] **Step 5: 커밋**
```bash
./gradlew :apps:commerce-streamer:ktlintCheck -q
git add apps/commerce-streamer/src/main/kotlin/com/loopers/domain/metrics/ \
        apps/commerce-streamer/src/main/kotlin/com/loopers/infrastructure/metrics/ \
        apps/commerce-streamer/src/main/kotlin/com/loopers/application/metrics/ \
        apps/commerce-streamer/build.gradle.kts \
        apps/commerce-streamer/src/test/kotlin/com/loopers/application/metrics/ProductMetricsServiceIntegrationTest.kt
git commit -m "feat: product_metrics + event_handled idempotent upsert service (R7-B2)"
```

---

### Task 2: 메시지 DTO + 파싱/라우팅 (message → MetricDelta)

**Files:**
- Create: `apps/commerce-streamer/src/main/kotlin/com/loopers/application/metrics/MetricEventMapper.kt`
- Test: `apps/commerce-streamer/src/test/kotlin/com/loopers/application/metrics/MetricEventMapperTest.kt`

**Interfaces:**
- `MetricEventMapper.toCommand(json: String): MetricCommand?` — JSON payload → `MetricCommand(eventId, deltas: List<MetricDelta>)`. 알 수 없는 type이면 null(무시).

- [ ] **Step 1: 실패 테스트 작성** — `MetricEventMapperTest.kt` (순수 파싱, ObjectMapper):

```kotlin
package com.loopers.application.metrics

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class MetricEventMapperTest {
    private val mapper = MetricEventMapper(ObjectMapper())

    @DisplayName("LIKE_ADDED 는 like=+1 delta로 매핑된다.")
    @Test
    fun mapsLikeAdded() {
        val cmd = mapper.toCommand("""{"eventId":"e1","type":"LIKE_ADDED","productId":10}""")!!
        assertThat(cmd.eventId).isEqualTo("e1")
        assertThat(cmd.deltas).containsExactly(MetricDelta(productId = 10L, like = 1))
    }

    @DisplayName("LIKE_REMOVED 는 like=-1 delta로 매핑된다.")
    @Test
    fun mapsLikeRemoved() {
        val cmd = mapper.toCommand("""{"eventId":"e2","type":"LIKE_REMOVED","productId":10}""")!!
        assertThat(cmd.deltas).containsExactly(MetricDelta(productId = 10L, like = -1))
    }

    @DisplayName("PRODUCT_VIEWED 는 view=+1 delta로 매핑된다.")
    @Test
    fun mapsProductViewed() {
        val cmd = mapper.toCommand("""{"eventId":"e3","type":"PRODUCT_VIEWED","productId":10}""")!!
        assertThat(cmd.deltas).containsExactly(MetricDelta(productId = 10L, view = 1))
    }

    @DisplayName("PAYMENT_SUCCEEDED 는 item별 sales delta로 매핑된다.")
    @Test
    fun mapsPaymentSucceeded() {
        val json = """{"eventId":"e4","type":"PAYMENT_SUCCEEDED","orderId":1,"userId":2,"items":[{"productId":10,"quantity":3},{"productId":20,"quantity":1}]}"""
        val cmd = mapper.toCommand(json)!!
        assertThat(cmd.deltas).containsExactly(
            MetricDelta(productId = 10L, sales = 3),
            MetricDelta(productId = 20L, sales = 1),
        )
    }

    @DisplayName("알 수 없는 type은 null(무시).")
    @Test
    fun ignoresUnknownType() {
        assertThat(mapper.toCommand("""{"eventId":"e5","type":"WHATEVER"}""")).isNull()
    }
}
```

- [ ] **Step 2: 실패 확인** — `--tests "com.loopers.application.metrics.MetricEventMapperTest"` → FAIL.

- [ ] **Step 3: 구현** — `application/metrics/MetricEventMapper.kt`:
```kotlin
package com.loopers.application.metrics

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

data class MetricCommand(val eventId: String, val deltas: List<MetricDelta>)

@Component
class MetricEventMapper(
    private val objectMapper: ObjectMapper,
) {
    fun toCommand(json: String): MetricCommand? {
        val node = objectMapper.readTree(json)
        val eventId = node["eventId"]?.asText() ?: return null
        val deltas = when (node["type"]?.asText()) {
            "LIKE_ADDED" -> listOf(MetricDelta(productId = node["productId"].asLong(), like = 1))
            "LIKE_REMOVED" -> listOf(MetricDelta(productId = node["productId"].asLong(), like = -1))
            "PRODUCT_VIEWED" -> listOf(MetricDelta(productId = node["productId"].asLong(), view = 1))
            "PAYMENT_SUCCEEDED" -> node["items"].map {
                MetricDelta(productId = it["productId"].asLong(), sales = it["quantity"].asLong())
            }
            else -> return null
        }
        return MetricCommand(eventId, deltas)
    }
}

private fun JsonNode.asLong() = this.asLong()
```
> 주의: `node["items"].map{}` 는 `JsonNode`가 Iterable이라 동작. `productId`/`quantity`는 `.asLong()`. (마지막 private 확장은 불필요하면 제거 — 실제로는 `JsonNode.asLong()`가 내장이므로 삭제하고 `node["productId"].asLong()` 그대로 사용.)

- [ ] **Step 4: 통과 확인** — test PASS.

- [ ] **Step 5: 커밋**
```bash
./gradlew :apps:commerce-streamer:ktlintCheck -q
git add apps/commerce-streamer/src/main/kotlin/com/loopers/application/metrics/MetricEventMapper.kt \
        apps/commerce-streamer/src/test/kotlin/com/loopers/application/metrics/MetricEventMapperTest.kt
git commit -m "feat: metric event mapper (message JSON → deltas) (R7-B2)"
```

---

### Task 3: Kafka Consumer(@KafkaListener) + end-to-end 통합테스트

**Files:**
- Create: `apps/commerce-streamer/src/main/kotlin/com/loopers/interfaces/consumer/MetricsConsumer.kt`
- Modify: `apps/commerce-streamer/src/main/resources/application.yml` (`spring.application.name: commerce-streamer` 오타 수정)
- Test: `apps/commerce-streamer/src/test/kotlin/com/loopers/interfaces/consumer/MetricsConsumerIntegrationTest.kt`

**Interfaces:**
- Consumes: `MetricEventMapper`, `ProductMetricsService`. `MetricsConsumer` — `@KafkaListener(topics=["catalog-events","order-events"], containerFactory=KafkaConfig.BATCH_LISTENER)`, `List<ConsumerRecord<String, ByteArray>>` + `Acknowledgment`. 각 레코드 payload(UTF-8 JSON) → mapper → service.applyOnce, 배치 끝에 ack.

- [ ] **Step 1: 실패 테스트 작성** — `MetricsConsumerIntegrationTest.kt` (MySQL + Kafka 컨테이너; 프로듀서로 메시지 발행 → 컨슈머 처리 → product_metrics 폴링 검증 + 중복 무시):

```kotlin
package com.loopers.interfaces.consumer

import com.loopers.domain.metrics.ProductMetricRepository
import com.loopers.utils.DatabaseCleanUp
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import java.time.Duration

@SpringBootTest
class MetricsConsumerIntegrationTest {
    @Autowired lateinit var metricRepository: ProductMetricRepository
    @Autowired lateinit var databaseCleanUp: DatabaseCleanUp
    @Value("\${spring.kafka.bootstrap-servers}") lateinit var bootstrap: String

    @AfterEach fun tearDown() = databaseCleanUp.truncateAllTables()

    @DisplayName("catalog-events LIKE_ADDED 를 소비하면 product_metrics.like_count가 증가한다.")
    @Test
    fun consumesLikeAdded() {
        publish("catalog-events", "10", """{"eventId":"c1","type":"LIKE_ADDED","productId":10}""")
        val metric = awaitMetric(10L)
        assertThat(metric?.likeCount).isEqualTo(1L)
    }

    @DisplayName("같은 eventId를 두 번 발행해도 like_count는 1만 반영된다(멱등).")
    @Test
    fun idempotentAcrossDuplicateMessages() {
        publish("catalog-events", "20", """{"eventId":"c2","type":"LIKE_ADDED","productId":20}""")
        publish("catalog-events", "20", """{"eventId":"c2","type":"LIKE_ADDED","productId":20}""")
        Thread.sleep(3000)
        assertThat(metricRepository.findByProductId(20L)?.likeCount).isEqualTo(1L)
    }

    private fun awaitMetric(productId: Long) = run {
        var m = metricRepository.findByProductId(productId)
        var tries = 0
        while (m == null && tries < 50) { Thread.sleep(200); m = metricRepository.findByProductId(productId); tries++ }
        m
    }

    private fun publish(topic: String, key: String, value: String) {
        val props = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrap,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
        )
        KafkaProducer<String, String>(props).use { it.send(ProducerRecord(topic, key, value)).get() }
    }
}
```
> `Thread.sleep` 폴링은 awaitility 미도입이라 사용. 컨슈머가 백그라운드로 처리하므로 폴링 대기 필요.

- [ ] **Step 2: 실패 확인** — `--tests "com.loopers.interfaces.consumer.MetricsConsumerIntegrationTest"` → FAIL(컨슈머 없음 → metric null → timeout).

- [ ] **Step 3: 구현** — `interfaces/consumer/MetricsConsumer.kt`:
```kotlin
package com.loopers.interfaces.consumer

import com.loopers.application.metrics.MetricEventMapper
import com.loopers.application.metrics.ProductMetricsService
import com.loopers.config.kafka.KafkaConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class MetricsConsumer(
    private val mapper: MetricEventMapper,
    private val service: ProductMetricsService,
) {
    private val log = LoggerFactory.getLogger(MetricsConsumer::class.java)

    @KafkaListener(
        topics = ["catalog-events", "order-events"],
        containerFactory = KafkaConfig.BATCH_LISTENER,
    )
    fun consume(
        records: List<ConsumerRecord<String, ByteArray>>,
        acknowledgment: Acknowledgment,
    ) {
        records.forEach { record ->
            val json = String(record.value(), Charsets.UTF_8)
            val command = mapper.toCommand(json)
            if (command == null) {
                log.warn("Unknown metric event skipped. topic={} payload={}", record.topic(), json)
                return@forEach
            }
            service.applyOnce(command.eventId, command.deltas)
        }
        acknowledgment.acknowledge()
    }
}
```
> 처리 중 예외가 나면 ack 안 됨 → 재소비. 이미 반영된 event는 `event_handled`로 skip되어 멱등. 개별 레코드 실패 격리(DLQ)는 Nice-to-have.

`application.yml` — `spring.application.name`을 `commerce-streamer`로 정정(현재 `commerce-api` 오타).

- [ ] **Step 4: 통과 확인** — Docker(MySQL+Kafka). test PASS.

- [ ] **Step 5: 전체 회귀 + 커밋**
```bash
./gradlew :apps:commerce-streamer:test -q
./gradlew :apps:commerce-streamer:ktlintCheck -q
git add apps/commerce-streamer/src/main/kotlin/com/loopers/interfaces/consumer/MetricsConsumer.kt \
        apps/commerce-streamer/src/main/resources/application.yml \
        apps/commerce-streamer/src/test/kotlin/com/loopers/interfaces/consumer/MetricsConsumerIntegrationTest.kt
git commit -m "feat: metrics kafka consumer (catalog/order-events → product_metrics) (R7-B2)"
```

---

## Step B2 완료 기준 (DoD)

- `catalog-events`/`order-events` 소비 → `product_metrics`(like/sales/view) upsert 집계.
- `event_handled(event_id PK)`로 중복 메시지 멱등 (재수신에도 1회 반영).
- manual ack (처리 완료 후 커밋).
- `./gradlew :apps:commerce-streamer:test`, `:apps:commerce-streamer:ktlintCheck` 통과. (commerce-api/batch 영향 없음 — streamer만 변경)

## Self-Review (spec 대비)

- **Consumer가 metrics 집계(product_metrics upsert)** → Task 1(service)+Task 3(consumer). ✅
- **event_handled 멱등(spec §7, "왜 로그와 분리?")** → Task 1. event_handled = dedup 전용(핫패스, PK만). ✅
- **manual Ack + version/updated_at 최신** → Task 3 manual ack. 증분 카운터는 event_handled 멱등이 주 방어(spec §7 최신성 노트대로). ✅
- **PartitionKey 순서** → key=productId/orderId(B1 발행). 동일 상품/주문 직렬화. sales 상품-교차 경합은 원자적 upsert. ✅
- **streamer commerce-api 비의존** → 자체 DTO/파싱. ✅
- 미커버(의도적): DLQ, consumer group 분리, batch 처리 튜닝 = Nice-to-have. 선착순 = Plan C.
- 타입 일관성: `MetricDelta`/`MetricCommand` 생산(T2)=소비(T1 service, T3 consumer). 메시지 계약(B1 발행) = mapper 파싱(T2).

## 리스크 / 주의

- **BATCH_LISTENER value = ByteArray**(kafka.yml consumer value-deserializer=ByteArrayDeserializer). 레코드 payload를 `String(bytes, UTF-8)` 후 파싱. (kafka.yml의 consumer `value-serializer` 키는 오타지만 Spring이 `value-deserializer` 기본/실제 ByteArray로 동작 — 소비 정상 확인 필요.)
- **product_metrics 원자적 upsert**: `INSERT..ON DUPLICATE KEY UPDATE`는 MySQL 전용(테스트/운영 MySQL이라 OK). H2 등에선 미동작.
- **@KafkaListener 자동 소비**: streamer @SpringBootTest는 컨슈머가 백그라운드로 돌며 컨테이너 토픽 소비. 테스트는 폴링 대기.
- **Kafka Testcontainers always-on**: streamer @SpringBootTest도 Kafka+MySQL 컨테이너 기동(B1과 동일 패턴, testFixtures 스캔).
- **auto.offset.reset=latest**(kafka.yml): 컨슈머가 구독 전 발행된 메시지는 놓칠 수 있음 → 테스트는 컨텍스트 기동(구독) 후 발행하므로 OK. 필요 시 test에서 earliest 고려.
- Docker 필요(전 태스크 통합테스트).

## 다음
B2 완료 후 **Plan C**(Step 3 선착순 쿠폰: 요청 API → coupon-issue-requests 발행 → Consumer 순차 발급 + 수량 원자적 차감 + 결과 폴링 + 동시성 테스트).
