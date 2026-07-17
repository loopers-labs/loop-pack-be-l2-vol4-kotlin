# Round 9 — Redis ZSET 실시간 랭킹 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Kafka 이벤트(조회·좋아요·주문)를 별도 컨슈머 그룹이 소비해 Redis ZSET 일간/시간 랭킹에 점수를 적재하고, 랭킹 Page API와 상품 상세 rank를 제공한다.

**Architecture:** commerce-streamer에 `[ranking group]` RankingConsumer 신규(기존 MetricsConsumer 불변). SETNX dedup → ZINCRBY(daily+hourly) → EXPIREAT 절대시각. commerce-api는 ZREVRANGE 조회 + 상품정보 aggregation. 스펙: `docs/superpowers/specs/2026-07-17-round9-ranking-design.md`.

**Tech Stack:** Kotlin/Spring Boot 3.4, Spring Kafka(BATCH_LISTENER, manual ack), Spring Data Redis(Lettuce), JUnit5 + Testcontainers(MySQL/Redis/Kafka).

## Global Constraints

- 키(크로스 앱 계약, 스펙 D7): `ranking:all:v1:{yyyyMMdd}` · `ranking:hourly:v1:{yyyyMMddHH}` · `ranking:handled:v1:{eventId}`
- 점수(스펙 D3): view +0.1, like ±0.2, order +0.6×log10(1+unitPrice×quantity). 가중치는 `@Value("\${ranking.weight.*:기본값}")` 외부화
- TTL: EXPIREAT 절대시각 — daily=윈도우 시작+2일, hourly=윈도우 시작+2시간, dedup=`SET NX EX` 2일
- 컨슈머 그룹: `loopers-ranking-consumer` (기존 기본 그룹 `loopers-default-consumer`와 분리)
- 기존 R7 metrics 경로(`MetricsConsumer`/`ProductMetricsService`/`event_handled`)는 수정 금지
- 레이어: `domain/ranking`(port·정책) ← `application/ranking`(usecase·mapper) ← `infrastructure/ranking`(Redis) / `interfaces`(consumer·api)
- 커밋 전 `./gradlew ktlintFormat` 실행. 커밋 메시지에 `(R9-N)` 태그
- Redis 장애 degradation은 `DataAccessException`만 catch(R8 원칙)
- 테스트가 "client version 1.32 too old"(Docker 29 ↔ Testcontainers 비호환)로 죽으면 **Task 0** 먼저

---

### Task 0: 테스트 인프라 사전 점검 (조건부)

**Files:**
- Modify (조건부): `gradle.properties` 또는 루트 `build.gradle.kts`의 testcontainers 버전

- [ ] **Step 1: 기존 테스트 1개 실행으로 인프라 확인**

Run: `./gradlew :apps:commerce-streamer:test --tests "com.loopers.application.metrics.MetricEventMapperTest"`
Expected: PASS (Docker 불필요한 단위 테스트). 이어서 `./gradlew :apps:commerce-streamer:test --tests "com.loopers.application.metrics.ProductMetricsServiceIntegrationTest"` — PASS면 Task 1로. `client version 1.32 too old` 에러면 Step 2.

- [ ] **Step 2 (조건부): testcontainers 버전 bump**

루트 빌드에서 testcontainers BOM/버전 선언 위치를 찾아(`grep -rn "testcontainers" --include="*.gradle.kts" --include="*.toml" .`) 최신 안정 버전(1.21.x 이상)으로 올리고 Step 1 재실행.

- [ ] **Step 3 (조건부): Commit**

```bash
git add -A && git commit -m "chore: testcontainers 버전 bump (Docker 29 호환) (R9-0)"
```

---

### Task 1: PAYMENT_SUCCEEDED 페이로드에 unitPrice 추가 (commerce-api)

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/payment/PaymentEvent.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/payment/usecase/SyncPaymentResultUsecase.kt` (PgStatus.SUCCESS 분기, ~L87-95)
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/outbox/OutboxMessageFactory.kt` (`order()`)
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/application/outbox/OutboxMessageFactoryTest.kt`

**Interfaces:**
- Produces: `PaymentSucceededEvent.Item(productId: Long, quantity: Int, unitPrice: BigDecimal)` — 페이로드 JSON `items[].unitPrice` (숫자). Task 5의 streamer 매퍼가 이 필드를 파싱.
- 참고: streamer `MetricEventMapper`는 `productId`/`quantity`만 읽으므로 필드 추가에 영향 없음(수정 금지).

- [ ] **Step 1: 실패하는 테스트 — `mapsPaymentSucceeded`에 unitPrice 검증 추가**

`OutboxMessageFactoryTest.mapsPaymentSucceeded()`를 다음으로 교체:

```kotlin
@DisplayName("결제성공 이벤트는 order-events, key=orderId, items(unitPrice 포함)로 매핑된다.")
@Test
fun mapsPaymentSucceeded() {
    val event = PaymentSucceededEvent(
        orderId = 1L,
        userId = 2L,
        items = listOf(PaymentSucceededEvent.Item(productId = 10L, quantity = 3, unitPrice = BigDecimal("15000.00"))),
    )
    val draft = factory.from(event)!!
    assertThat(draft.topic).isEqualTo(KafkaTopics.ORDER_EVENTS)
    assertThat(draft.partitionKey).isEqualTo("1")
    val node = om.readTree(draft.payload)
    assertThat(node["type"].asText()).isEqualTo("PAYMENT_SUCCEEDED")
    assertThat(node["items"][0]["productId"].asLong()).isEqualTo(10L)
    assertThat(node["items"][0]["quantity"].asInt()).isEqualTo(3)
    assertThat(node["items"][0]["unitPrice"].asDouble()).isEqualTo(15000.0)
}
```

import 추가: `java.math.BigDecimal`

- [ ] **Step 2: 컴파일 실패 확인**

Run: `./gradlew :apps:commerce-api:compileTestKotlin`
Expected: FAIL — `Item`에 `unitPrice` 파라미터 없음

- [ ] **Step 3: 구현**

`PaymentEvent.kt`:

```kotlin
data class Item(
    val productId: Long,
    val quantity: Int,
    val unitPrice: BigDecimal,
)
```

(import `java.math.BigDecimal`)

`SyncPaymentResultUsecase.kt`의 발행 지점 — `OrderItemModel.price`가 단가:

```kotlin
items = order.items.map {
    PaymentSucceededEvent.Item(productId = it.productId, quantity = it.quantity, unitPrice = it.price)
},
```

`OutboxMessageFactory.order()`:

```kotlin
"items" to event.items.map {
    linkedMapOf("productId" to it.productId, "quantity" to it.quantity, "unitPrice" to it.unitPrice)
},
```

- [ ] **Step 4: 전체 api 테스트로 다른 Item 생성처 컴파일 확인**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.outbox.OutboxMessageFactoryTest" --tests "com.loopers.application.payment.*"`
Expected: PASS. 컴파일 에러가 나는 다른 테스트(`SyncPaymentResultUsecaseIntegrationTest` 등에서 `Item(` 생성 시)는 `unitPrice = BigDecimal("10000.00")` 식으로 보정 (`NotificationEventHandlerTest`/`UserActionLogEventHandlerTest`는 `items = emptyList()`라 무관).

- [ ] **Step 5: Commit**

```bash
./gradlew ktlintFormat
git add -A && git commit -m "feat: PAYMENT_SUCCEEDED 페이로드에 unitPrice 추가 (R9-1)"
```

---

### Task 2: RankingScorePolicy — 가중치 점수 정책 (commerce-streamer)

**Files:**
- Create: `apps/commerce-streamer/src/main/kotlin/com/loopers/domain/ranking/RankingScorePolicy.kt`
- Test: `apps/commerce-streamer/src/test/kotlin/com/loopers/domain/ranking/RankingScorePolicyTest.kt`

**Interfaces:**
- Produces: `RankingScorePolicy(viewWeight, likeWeight, orderWeight)` — `viewed(): Double`, `likeAdded(): Double`, `likeRemoved(): Double`, `ordered(unitPrice: BigDecimal, quantity: Int): Double`. Task 5 매퍼가 사용.

- [ ] **Step 1: 실패하는 테스트**

```kotlin
package com.loopers.domain.ranking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.math.log10

class RankingScorePolicyTest {
    private val policy = RankingScorePolicy(viewWeight = 0.1, likeWeight = 0.2, orderWeight = 0.6)

    @DisplayName("조회는 +0.1, 좋아요는 +0.2, 좋아요 취소는 -0.2 점수를 만든다.")
    @Test
    fun basicScores() {
        assertThat(policy.viewed()).isEqualTo(0.1)
        assertThat(policy.likeAdded()).isEqualTo(0.2)
        assertThat(policy.likeRemoved()).isEqualTo(-0.2)
    }

    @DisplayName("주문 점수는 0.6 × log10(1 + 단가×수량)이다.")
    @Test
    fun orderScoreIsLogNormalized() {
        val score = policy.ordered(unitPrice = BigDecimal("30000"), quantity = 1)
        assertThat(score).isCloseTo(0.6 * log10(1.0 + 30000.0), org.assertj.core.data.Offset.offset(1e-9))
    }

    @DisplayName("주문 1건(3만원)이 좋아요 3건보다 점수가 높다 — 체크리스트 검증.")
    @Test
    fun oneOrderBeatsThreeLikes() {
        val oneOrder = policy.ordered(unitPrice = BigDecimal("30000"), quantity = 1)
        val threeLikes = policy.likeAdded() * 3
        assertThat(oneOrder).isGreaterThan(threeLikes)
    }

    @DisplayName("단가 0원 주문은 0점이다 (log10(1)=0).")
    @Test
    fun zeroPriceOrderScoresZero() {
        assertThat(policy.ordered(unitPrice = BigDecimal.ZERO, quantity = 5)).isEqualTo(0.0)
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :apps:commerce-streamer:test --tests "com.loopers.domain.ranking.RankingScorePolicyTest"`
Expected: FAIL — `RankingScorePolicy` 미존재(컴파일 에러)

- [ ] **Step 3: 구현**

```kotlin
package com.loopers.domain.ranking

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.math.BigDecimal
import kotlin.math.log10

@Component
class RankingScorePolicy(
    @Value("\${ranking.weight.view:0.1}") private val viewWeight: Double,
    @Value("\${ranking.weight.like:0.2}") private val likeWeight: Double,
    @Value("\${ranking.weight.order:0.6}") private val orderWeight: Double,
) {
    fun viewed(): Double = viewWeight

    fun likeAdded(): Double = likeWeight

    fun likeRemoved(): Double = -likeWeight

    // 금액은 log 스케일로 정규화 — 원금액 합산 시 주문이 조회/좋아요 신호를 지배하는 문제 방지(스펙 D3)
    fun ordered(unitPrice: BigDecimal, quantity: Int): Double =
        orderWeight * log10(1.0 + unitPrice.toDouble() * quantity)
}
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew :apps:commerce-streamer:test --tests "com.loopers.domain.ranking.RankingScorePolicyTest"`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
./gradlew ktlintFormat
git add -A && git commit -m "feat: 랭킹 가중치 점수 정책 — log10 정규화 주문 점수 (R9-2)"
```

---

### Task 3: RankingKeyResolver — 윈도우 키·만료시각 계산 (commerce-streamer)

**Files:**
- Create: `apps/commerce-streamer/src/main/kotlin/com/loopers/domain/ranking/RankingKeyResolver.kt`
- Test: `apps/commerce-streamer/src/test/kotlin/com/loopers/domain/ranking/RankingKeyResolverTest.kt`

**Interfaces:**
- Produces: `RankingWindow(dailyKey: String, hourlyKey: String, dailyExpireAt: Instant, hourlyExpireAt: Instant)`, `RankingKeyResolver.windowFor(now: ZonedDateTime): RankingWindow`. Task 4 repository·Task 5 service가 사용.

- [ ] **Step 1: 실패하는 테스트**

```kotlin
package com.loopers.domain.ranking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class RankingKeyResolverTest {
    private val resolver = RankingKeyResolver()
    private val zone = ZoneId.of("Asia/Seoul")

    @DisplayName("일간 키는 ranking:all:v1:yyyyMMdd, 시간 키는 ranking:hourly:v1:yyyyMMddHH 형식이다.")
    @Test
    fun keyFormats() {
        val now = ZonedDateTime.of(2026, 7, 17, 14, 30, 0, 0, zone)
        val window = resolver.windowFor(now)
        assertThat(window.dailyKey).isEqualTo("ranking:all:v1:20260717")
        assertThat(window.hourlyKey).isEqualTo("ranking:hourly:v1:2026071714")
    }

    @DisplayName("만료시각은 윈도우 시작 + 2×윈도우 크기 절대시각이다 (daily +2일, hourly +2시간).")
    @Test
    fun expireAtIsAbsolute() {
        val now = ZonedDateTime.of(2026, 7, 17, 14, 30, 45, 0, zone)
        val window = resolver.windowFor(now)
        assertThat(window.dailyExpireAt)
            .isEqualTo(ZonedDateTime.of(2026, 7, 19, 0, 0, 0, 0, zone).toInstant())
        assertThat(window.hourlyExpireAt)
            .isEqualTo(ZonedDateTime.of(2026, 7, 17, 16, 0, 0, 0, zone).toInstant())
    }

    @DisplayName("자정 직전/직후는 다른 일간 키를 만든다 — 윈도우 경계.")
    @Test
    fun midnightBoundary() {
        val beforeMidnight = ZonedDateTime.of(2026, 7, 17, 23, 59, 59, 0, zone)
        val afterMidnight = ZonedDateTime.of(2026, 7, 18, 0, 0, 0, 0, zone)
        assertThat(resolver.windowFor(beforeMidnight).dailyKey).isEqualTo("ranking:all:v1:20260717")
        assertThat(resolver.windowFor(afterMidnight).dailyKey).isEqualTo("ranking:all:v1:20260718")
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :apps:commerce-streamer:test --tests "com.loopers.domain.ranking.RankingKeyResolverTest"`
Expected: FAIL — 컴파일 에러

- [ ] **Step 3: 구현**

```kotlin
package com.loopers.domain.ranking

import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class RankingWindow(
    val dailyKey: String,
    val hourlyKey: String,
    val dailyExpireAt: Instant,
    val hourlyExpireAt: Instant,
)

@Component
class RankingKeyResolver {
    fun windowFor(now: ZonedDateTime): RankingWindow {
        val dayStart = now.truncatedTo(ChronoUnit.DAYS)
        val hourStart = now.truncatedTo(ChronoUnit.HOURS)
        return RankingWindow(
            dailyKey = DAILY_KEY_PREFIX + now.format(DAILY_FORMAT),
            hourlyKey = HOURLY_KEY_PREFIX + now.format(HOURLY_FORMAT),
            dailyExpireAt = dayStart.plusDays(2).toInstant(),
            hourlyExpireAt = hourStart.plusHours(2).toInstant(),
        )
    }

    companion object {
        const val DAILY_KEY_PREFIX = "ranking:all:v1:"
        const val HOURLY_KEY_PREFIX = "ranking:hourly:v1:"
        private val DAILY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val HOURLY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHH")
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew :apps:commerce-streamer:test --tests "com.loopers.domain.ranking.RankingKeyResolverTest"`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
./gradlew ktlintFormat
git add -A && git commit -m "feat: 랭킹 윈도우 키·EXPIREAT 계산 (R9-3)"
```

---

### Task 4: RankingRepository port + Redis 구현 — SETNX dedup·ZINCRBY·EXPIREAT (commerce-streamer)

**Files:**
- Create: `apps/commerce-streamer/src/main/kotlin/com/loopers/domain/ranking/RankingRepository.kt`
- Create: `apps/commerce-streamer/src/main/kotlin/com/loopers/infrastructure/ranking/RankingRedisRepository.kt`
- Test: `apps/commerce-streamer/src/test/kotlin/com/loopers/infrastructure/ranking/RankingRedisRepositoryIntegrationTest.kt`

**Interfaces:**
- Consumes: `RankingWindow` (Task 3)
- Produces: `RankingScoreDelta(productId: Long, score: Double)`, `RankingScoreEntry(eventId: String, deltas: List<RankingScoreDelta>)`, `RankingRepository.applyAll(entries: List<RankingScoreEntry>, window: RankingWindow): Int`(적용 건수). dedup 키 prefix `ranking:handled:v1:`.

- [ ] **Step 1: 실패하는 테스트**

```kotlin
package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.RankingRepository
import com.loopers.domain.ranking.RankingScoreDelta
import com.loopers.domain.ranking.RankingScoreEntry
import com.loopers.domain.ranking.RankingWindow
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import java.time.Instant
import java.time.temporal.ChronoUnit

@SpringBootTest
class RankingRedisRepositoryIntegrationTest @Autowired constructor(
    private val rankingRepository: RankingRepository,
    private val redisTemplate: RedisTemplate<String, String>,
    private val redisCleanUp: RedisCleanUp,
) {
    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
    }

    private val window = RankingWindow(
        dailyKey = "ranking:all:v1:20260717",
        hourlyKey = "ranking:hourly:v1:2026071714",
        dailyExpireAt = Instant.now().plus(2, ChronoUnit.DAYS),
        hourlyExpireAt = Instant.now().plus(2, ChronoUnit.HOURS),
    )

    @DisplayName("엔트리의 델타가 daily/hourly ZSET에 모두 가산되고 TTL이 설정된다.")
    @Test
    fun appliesDeltasToBothWindows() {
        val applied = rankingRepository.applyAll(
            listOf(
                RankingScoreEntry("evt-1", listOf(RankingScoreDelta(productId = 10L, score = 0.1))),
                RankingScoreEntry("evt-2", listOf(RankingScoreDelta(productId = 10L, score = 0.2))),
            ),
            window,
        )

        assertThat(applied).isEqualTo(2)
        assertThat(redisTemplate.opsForZSet().score(window.dailyKey, "10")).isCloseTo(0.3, org.assertj.core.data.Offset.offset(1e-9))
        assertThat(redisTemplate.opsForZSet().score(window.hourlyKey, "10")).isCloseTo(0.3, org.assertj.core.data.Offset.offset(1e-9))
        assertThat(redisTemplate.getExpire(window.dailyKey)).isGreaterThan(0L)
        assertThat(redisTemplate.getExpire(window.hourlyKey)).isGreaterThan(0L)
    }

    @DisplayName("같은 eventId 재적용은 걸러져 점수가 중복 가산되지 않는다 — SETNX dedup.")
    @Test
    fun deduplicatesByEventId() {
        val entries = listOf(RankingScoreEntry("evt-dup", listOf(RankingScoreDelta(10L, 0.5))))

        val first = rankingRepository.applyAll(entries, window)
        val second = rankingRepository.applyAll(entries, window)

        assertThat(first).isEqualTo(1)
        assertThat(second).isEqualTo(0)
        assertThat(redisTemplate.opsForZSet().score(window.dailyKey, "10")).isCloseTo(0.5, org.assertj.core.data.Offset.offset(1e-9))
    }

    @DisplayName("음수 델타(좋아요 취소)는 점수를 감산한다.")
    @Test
    fun negativeDeltaDecreasesScore() {
        rankingRepository.applyAll(listOf(RankingScoreEntry("evt-a", listOf(RankingScoreDelta(10L, 0.2)))), window)
        rankingRepository.applyAll(listOf(RankingScoreEntry("evt-b", listOf(RankingScoreDelta(10L, -0.2)))), window)

        assertThat(redisTemplate.opsForZSet().score(window.dailyKey, "10")).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-9))
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :apps:commerce-streamer:test --tests "com.loopers.infrastructure.ranking.RankingRedisRepositoryIntegrationTest"`
Expected: FAIL — 컴파일 에러(port/구현 미존재)

- [ ] **Step 3: port + 구현**

`domain/ranking/RankingRepository.kt`:

```kotlin
package com.loopers.domain.ranking

data class RankingScoreDelta(
    val productId: Long,
    val score: Double,
)

data class RankingScoreEntry(
    val eventId: String,
    val deltas: List<RankingScoreDelta>,
)

interface RankingRepository {
    /**
     * eventId dedup(SET NX)을 통과한 엔트리만 daily/hourly ZSET에 가산하고 적용 건수를 반환한다.
     * 윈도우 키에는 절대시각 만료(EXPIREAT)를 설정한다.
     */
    fun applyAll(entries: List<RankingScoreEntry>, window: RankingWindow): Int
}
```

`infrastructure/ranking/RankingRedisRepository.kt`:

```kotlin
package com.loopers.infrastructure.ranking

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.ranking.RankingRepository
import com.loopers.domain.ranking.RankingScoreEntry
import com.loopers.domain.ranking.RankingWindow
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.connection.RedisStringCommands
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.types.Expiration
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class RankingRedisRepository(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
) : RankingRepository {
    override fun applyAll(entries: List<RankingScoreEntry>, window: RankingWindow): Int {
        if (entries.isEmpty()) return 0
        val fresh = dedupPass(entries)
        if (fresh.isEmpty()) return 0
        incrementPass(fresh, window)
        return fresh.size
    }

    // pass 1: 이벤트별 SET NX 파이프라인 — 재소비(중복) 이벤트를 걸러낸다.
    private fun dedupPass(entries: List<RankingScoreEntry>): List<RankingScoreEntry> {
        val results = redisTemplate.executePipelined { connection ->
            entries.forEach { entry ->
                connection.stringCommands().set(
                    raw(DEDUP_KEY_PREFIX + entry.eventId),
                    raw("1"),
                    Expiration.from(DEDUP_TTL),
                    RedisStringCommands.SetOption.SET_IF_ABSENT,
                )
            }
            null
        }
        return entries.filterIndexed { index, _ -> results[index] == true }
    }

    // pass 2: 통과 엔트리만 daily/hourly ZINCRBY + 윈도우 절대시각 만료.
    private fun incrementPass(entries: List<RankingScoreEntry>, window: RankingWindow) {
        redisTemplate.executePipelined { connection ->
            entries.flatMap { it.deltas }.forEach { delta ->
                val member = raw(delta.productId.toString())
                connection.zSetCommands().zIncrBy(raw(window.dailyKey), delta.score, member)
                connection.zSetCommands().zIncrBy(raw(window.hourlyKey), delta.score, member)
            }
            connection.keyCommands().pExpireAt(raw(window.dailyKey), window.dailyExpireAt.toEpochMilli())
            connection.keyCommands().pExpireAt(raw(window.hourlyKey), window.hourlyExpireAt.toEpochMilli())
            null
        }
    }

    private fun raw(value: String): ByteArray = redisTemplate.stringSerializer.serialize(value)!!

    companion object {
        const val DEDUP_KEY_PREFIX = "ranking:handled:v1:"
        private val DEDUP_TTL = Duration.ofDays(2)
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew :apps:commerce-streamer:test --tests "com.loopers.infrastructure.ranking.RankingRedisRepositoryIntegrationTest"`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
./gradlew ktlintFormat
git add -A && git commit -m "feat: 랭킹 Redis 리포지토리 — SETNX dedup + ZINCRBY 2-pass 파이프라인 (R9-4)"
```

---

### Task 5: RankingEventMapper + RankingService (commerce-streamer)

**Files:**
- Create: `apps/commerce-streamer/src/main/kotlin/com/loopers/application/ranking/RankingEventMapper.kt`
- Create: `apps/commerce-streamer/src/main/kotlin/com/loopers/application/ranking/RankingService.kt`
- Test: `apps/commerce-streamer/src/test/kotlin/com/loopers/application/ranking/RankingEventMapperTest.kt`

**Interfaces:**
- Consumes: `RankingScorePolicy`(Task 2), `RankingKeyResolver`(Task 3), `RankingRepository`(Task 4)
- Produces: `RankingEventMapper.toEntry(json: String): RankingScoreEntry?`(unknown type/필드 결손 → null), `RankingService.apply(jsons: List<String>)`. Task 6 컨슈머가 사용.

- [ ] **Step 1: 실패하는 매퍼 테스트**

```kotlin
package com.loopers.application.ranking

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.ranking.RankingScorePolicy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.math.log10

class RankingEventMapperTest {
    private val mapper = RankingEventMapper(
        objectMapper = ObjectMapper(),
        scorePolicy = RankingScorePolicy(viewWeight = 0.1, likeWeight = 0.2, orderWeight = 0.6),
    )

    @DisplayName("PRODUCT_VIEWED는 +0.1 델타 엔트리로 매핑된다.")
    @Test
    fun mapsProductViewed() {
        val entry = mapper.toEntry("""{"eventId":"e1","type":"PRODUCT_VIEWED","productId":10}""")!!
        assertThat(entry.eventId).isEqualTo("e1")
        assertThat(entry.deltas).hasSize(1)
        assertThat(entry.deltas[0].productId).isEqualTo(10L)
        assertThat(entry.deltas[0].score).isEqualTo(0.1)
    }

    @DisplayName("LIKE_ADDED는 +0.2, LIKE_REMOVED는 -0.2로 매핑된다.")
    @Test
    fun mapsLikeEvents() {
        val added = mapper.toEntry("""{"eventId":"e2","type":"LIKE_ADDED","productId":10}""")!!
        val removed = mapper.toEntry("""{"eventId":"e3","type":"LIKE_REMOVED","productId":10}""")!!
        assertThat(added.deltas[0].score).isEqualTo(0.2)
        assertThat(removed.deltas[0].score).isEqualTo(-0.2)
    }

    @DisplayName("PAYMENT_SUCCEEDED는 아이템별 0.6×log10(1+단가×수량) 델타로 매핑된다.")
    @Test
    fun mapsPaymentSucceeded() {
        val entry = mapper.toEntry(
            """{"eventId":"e4","type":"PAYMENT_SUCCEEDED","orderId":1,"userId":2,
               "items":[{"productId":10,"quantity":2,"unitPrice":15000.00}]}""",
        )!!
        assertThat(entry.deltas[0].productId).isEqualTo(10L)
        assertThat(entry.deltas[0].score)
            .isCloseTo(0.6 * log10(1.0 + 30000.0), org.assertj.core.data.Offset.offset(1e-9))
    }

    @DisplayName("unitPrice가 없는 아이템은 건너뛴다 (구버전 페이로드 호환).")
    @Test
    fun skipsItemWithoutUnitPrice() {
        val entry = mapper.toEntry(
            """{"eventId":"e5","type":"PAYMENT_SUCCEEDED","items":[{"productId":10,"quantity":2}]}""",
        )!!
        assertThat(entry.deltas).isEmpty()
    }

    @DisplayName("알 수 없는 타입/eventId 결손은 null을 반환한다.")
    @Test
    fun returnsNullForUnknown() {
        assertThat(mapper.toEntry("""{"eventId":"e6","type":"COUPON_ISSUE_REQUESTED"}""")).isNull()
        assertThat(mapper.toEntry("""{"type":"PRODUCT_VIEWED","productId":10}""")).isNull()
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :apps:commerce-streamer:test --tests "com.loopers.application.ranking.RankingEventMapperTest"`
Expected: FAIL — 컴파일 에러

- [ ] **Step 3: 매퍼 + 서비스 구현**

`RankingEventMapper.kt` (`MetricEventMapper` 미러, 점수 계산은 정책 위임):

```kotlin
package com.loopers.application.ranking

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.ranking.RankingScoreDelta
import com.loopers.domain.ranking.RankingScoreEntry
import com.loopers.domain.ranking.RankingScorePolicy
import org.springframework.stereotype.Component

@Component
class RankingEventMapper(
    private val objectMapper: ObjectMapper,
    private val scorePolicy: RankingScorePolicy,
) {
    fun toEntry(json: String): RankingScoreEntry? {
        val node = objectMapper.readTree(json)
        val eventId = node["eventId"]?.asText() ?: return null
        val deltas = when (node["type"]?.asText()) {
            "PRODUCT_VIEWED" -> {
                val productId = node["productId"]?.asLong() ?: return null
                listOf(RankingScoreDelta(productId, scorePolicy.viewed()))
            }
            "LIKE_ADDED" -> {
                val productId = node["productId"]?.asLong() ?: return null
                listOf(RankingScoreDelta(productId, scorePolicy.likeAdded()))
            }
            "LIKE_REMOVED" -> {
                val productId = node["productId"]?.asLong() ?: return null
                listOf(RankingScoreDelta(productId, scorePolicy.likeRemoved()))
            }
            "PAYMENT_SUCCEEDED" -> {
                val items = node["items"] ?: return null
                items.mapNotNull {
                    val productId = it["productId"]?.asLong() ?: return@mapNotNull null
                    val quantity = it["quantity"]?.asInt() ?: return@mapNotNull null
                    val unitPrice = it["unitPrice"]?.decimalValue() ?: return@mapNotNull null
                    RankingScoreDelta(productId, scorePolicy.ordered(unitPrice, quantity))
                }
            }
            else -> return null
        }
        return RankingScoreEntry(eventId, deltas)
    }
}
```

`RankingService.kt`:

```kotlin
package com.loopers.application.ranking

import com.loopers.domain.ranking.RankingKeyResolver
import com.loopers.domain.ranking.RankingRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import java.time.ZonedDateTime

@Service
class RankingService(
    private val mapper: RankingEventMapper,
    private val keyResolver: RankingKeyResolver,
    private val rankingRepository: RankingRepository,
) {
    private val log = LoggerFactory.getLogger(RankingService::class.java)

    fun apply(jsons: List<String>) {
        val entries = jsons.mapNotNull { json ->
            runCatching { mapper.toEntry(json) }
                .getOrElse {
                    log.warn("Failed to parse ranking event, skipping.", it)
                    null
                }
        }
        if (entries.isEmpty()) return
        try {
            rankingRepository.applyAll(entries, keyResolver.windowFor(ZonedDateTime.now()))
        } catch (e: DataAccessException) {
            // 랭킹은 근사 집계 — Redis 장애 시 배치 스킵(경보 로그), 컨슈머는 생존(스펙 §7)
            log.error("Failed to apply ranking batch, skipping. size={}", entries.size, e)
        }
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew :apps:commerce-streamer:test --tests "com.loopers.application.ranking.RankingEventMapperTest"`
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
./gradlew ktlintFormat
git add -A && git commit -m "feat: 랭킹 이벤트 매퍼·서비스 — 정책 위임 점수 계산, DataAccessException 한정 degradation (R9-5)"
```

---

### Task 6: RankingConsumer — 별도 컨슈머 그룹 (commerce-streamer)

**Files:**
- Create: `apps/commerce-streamer/src/main/kotlin/com/loopers/interfaces/consumer/RankingConsumer.kt`
- Test: `apps/commerce-streamer/src/test/kotlin/com/loopers/interfaces/consumer/RankingConsumerIntegrationTest.kt`

**Interfaces:**
- Consumes: `RankingService.apply(jsons)` (Task 5), `RankingKeyResolver`(검증용)
- Produces: Kafka `catalog-events`/`order-events` → ZSET 반영 (그룹 `loopers-ranking-consumer`)

- [ ] **Step 1: 실패하는 통합 테스트** (`MetricsConsumerIntegrationTest` 패턴 미러)

```kotlin
package com.loopers.interfaces.consumer

import com.loopers.domain.ranking.RankingKeyResolver
import com.loopers.utils.RedisCleanUp
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.test.context.TestPropertySource
import java.time.ZonedDateTime
import java.util.Properties
import java.util.UUID
import kotlin.math.log10

@SpringBootTest
@TestPropertySource(properties = ["spring.kafka.properties.auto.offset.reset=earliest"])
class RankingConsumerIntegrationTest @Autowired constructor(
    private val redisTemplate: RedisTemplate<String, String>,
    private val redisCleanUp: RedisCleanUp,
) {
    @Value("\${spring.kafka.bootstrap-servers}")
    private lateinit var bootstrapServers: String

    private val keyResolver = RankingKeyResolver()

    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
    }

    private fun publish(topic: String, key: String, value: String) {
        val props = Properties().apply {
            put("bootstrap.servers", bootstrapServers)
            put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
            put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
        }
        KafkaProducer<String, String>(props).use { it.send(ProducerRecord(topic, key, value)).get() }
    }

    // 기대 점수에 수렴할 때까지 폴링 — 배치가 나뉘어 소비돼 점수가 부분 반영된 시점의 단언 플레이크 방지
    private fun awaitScore(key: String, member: String, expected: Double): Double? {
        var last: Double? = null
        repeat(50) {
            last = redisTemplate.opsForZSet().score(key, member)
            if (last != null && kotlin.math.abs(last!! - expected) < 1e-6) return last
            Thread.sleep(200)
        }
        return last
    }

    @DisplayName("조회/좋아요/주문 이벤트가 일간·시간 ZSET에 가중치 점수로 반영된다.")
    @Test
    fun consumesEventsIntoRankingZSets() {
        val window = keyResolver.windowFor(ZonedDateTime.now())
        val productId = 910L

        publish("catalog-events", "$productId", """{"eventId":"${UUID.randomUUID()}","type":"PRODUCT_VIEWED","productId":$productId}""")
        publish("catalog-events", "$productId", """{"eventId":"${UUID.randomUUID()}","type":"LIKE_ADDED","productId":$productId}""")

        assertThat(awaitScore(window.dailyKey, "$productId", expected = 0.3))
            .isCloseTo(0.3, org.assertj.core.data.Offset.offset(1e-6))
        assertThat(redisTemplate.opsForZSet().score(window.hourlyKey, "$productId"))
            .isCloseTo(0.3, org.assertj.core.data.Offset.offset(1e-6))
    }

    @DisplayName("같은 eventId를 두 번 발행해도 점수는 한 번만 반영된다 — 멱등.")
    @Test
    fun ignoresDuplicateEventId() {
        val window = keyResolver.windowFor(ZonedDateTime.now())
        val productId = 920L
        val eventId = UUID.randomUUID().toString()
        val payload = """{"eventId":"$eventId","type":"LIKE_ADDED","productId":$productId}"""

        publish("catalog-events", "$productId", payload)
        publish("catalog-events", "$productId", payload)

        assertThat(awaitScore(window.dailyKey, "$productId", expected = 0.2))
            .isCloseTo(0.2, org.assertj.core.data.Offset.offset(1e-6))
        Thread.sleep(3000)
        assertThat(redisTemplate.opsForZSet().score(window.dailyKey, "$productId"))
            .isCloseTo(0.2, org.assertj.core.data.Offset.offset(1e-6))
    }

    @DisplayName("주문 이벤트는 0.6×log10(1+단가×수량) 점수로 반영된다 — 주문 1건 > 좋아요 3건.")
    @Test
    fun orderEventOutweighsLikes() {
        val window = keyResolver.windowFor(ZonedDateTime.now())
        val orderedProduct = 930L
        val likedProduct = 931L

        publish(
            "order-events",
            "1",
            """{"eventId":"${UUID.randomUUID()}","type":"PAYMENT_SUCCEEDED","orderId":1,"userId":2,
               "items":[{"productId":$orderedProduct,"quantity":1,"unitPrice":30000.00}]}""",
        )
        repeat(3) {
            publish("catalog-events", "$likedProduct", """{"eventId":"${UUID.randomUUID()}","type":"LIKE_ADDED","productId":$likedProduct}""")
        }

        val expectedOrderScore = 0.6 * log10(1.0 + 30000.0)
        val orderScore = awaitScore(window.dailyKey, "$orderedProduct", expected = expectedOrderScore)!!
        val likeScore = awaitScore(window.dailyKey, "$likedProduct", expected = 0.6)!!
        assertThat(likeScore).isCloseTo(0.6, org.assertj.core.data.Offset.offset(1e-6))
        assertThat(orderScore).isGreaterThan(likeScore)
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :apps:commerce-streamer:test --tests "com.loopers.interfaces.consumer.RankingConsumerIntegrationTest"`
Expected: FAIL — 컨슈머 미존재라 ZSET에 점수가 안 쌓여 `awaitScore`가 null

- [ ] **Step 3: 구현**

```kotlin
package com.loopers.interfaces.consumer

import com.loopers.application.ranking.RankingService
import com.loopers.config.kafka.KafkaConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class RankingConsumer(
    private val rankingService: RankingService,
) {
    @KafkaListener(
        topics = ["catalog-events", "order-events"],
        groupId = GROUP_ID,
        containerFactory = KafkaConfig.BATCH_LISTENER,
    )
    fun consume(
        records: List<ConsumerRecord<String, ByteArray>>,
        acknowledgment: Acknowledgment,
    ) {
        rankingService.apply(records.map { String(it.value(), Charsets.UTF_8) })
        acknowledgment.acknowledge()
    }

    companion object {
        // metrics 그룹(loopers-default-consumer)과 분리 — 오프셋·장애·재소비 독립(스펙 D4)
        const val GROUP_ID = "loopers-ranking-consumer"
    }
}
```

- [ ] **Step 4: 통과 확인 + 기존 metrics 회귀 확인**

Run: `./gradlew :apps:commerce-streamer:test`
Expected: PASS — RankingConsumer 3 tests + 기존 Metrics 테스트 전부(같은 토픽을 두 그룹이 독립 소비)

- [ ] **Step 5: Commit**

```bash
./gradlew ktlintFormat
git add -A && git commit -m "feat: RankingConsumer — 별도 그룹으로 랭킹 ZSET 실시간 적재 (R9-6)"
```

---

### Task 7: RankingPeriod + RankingQueryRepository + Redis 구현 (commerce-api)

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/ranking/RankingPeriod.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/ranking/RankingQueryRepository.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/ranking/RankingRedisRepository.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/ranking/RankingPeriodTest.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/ranking/RankingRedisRepositoryTest.kt`

**Interfaces:**
- Produces: `RankingPeriod.{DAILY,HOURLY}` — `from(value: String?): RankingPeriod`(null→DAILY, 미지원→BAD_REQUEST), `resolveDate(date: String?, now: ZonedDateTime): String`(형식 오류→BAD_REQUEST), `key(resolvedDate: String): String`
- Produces: `RankedProduct(productId: Long, score: Double)`, `RankingQueryRepository.{page(key, offset, size): List<RankedProduct>, total(key): Long, rank(key, productId): Long?}` (rank는 0-based). Task 8·10이 사용.

- [ ] **Step 1: 실패하는 RankingPeriod 단위 테스트**

```kotlin
package com.loopers.domain.ranking

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class RankingPeriodTest {
    private val now = ZonedDateTime.of(2026, 7, 17, 14, 30, 0, 0, ZoneId.of("Asia/Seoul"))

    @DisplayName("period 미지정은 DAILY, hourly(대소문자 무관)는 HOURLY, 미지원 값은 BAD_REQUEST.")
    @Test
    fun fromParsesPeriod() {
        assertThat(RankingPeriod.from(null)).isEqualTo(RankingPeriod.DAILY)
        assertThat(RankingPeriod.from("HOURLY")).isEqualTo(RankingPeriod.HOURLY)
        assertThat(RankingPeriod.from("hourly")).isEqualTo(RankingPeriod.HOURLY)
        assertThatThrownBy { RankingPeriod.from("WEEKLY") }
            .isInstanceOf(CoreException::class.java)
            .extracting("errorType").isEqualTo(ErrorType.BAD_REQUEST)
    }

    @DisplayName("date 미지정은 현재 시각 기준으로 해석되고 키가 만들어진다.")
    @Test
    fun resolvesDefaultDate() {
        val daily = RankingPeriod.DAILY.resolveDate(null, now)
        val hourly = RankingPeriod.HOURLY.resolveDate(null, now)
        assertThat(daily).isEqualTo("20260717")
        assertThat(hourly).isEqualTo("2026071714")
        assertThat(RankingPeriod.DAILY.key(daily)).isEqualTo("ranking:all:v1:20260717")
        assertThat(RankingPeriod.HOURLY.key(hourly)).isEqualTo("ranking:hourly:v1:2026071714")
    }

    @DisplayName("형식이 잘못된 date는 BAD_REQUEST를 던진다.")
    @Test
    fun rejectsMalformedDate() {
        assertThatThrownBy { RankingPeriod.DAILY.resolveDate("2026-07-17", now) }
            .isInstanceOf(CoreException::class.java)
        assertThatThrownBy { RankingPeriod.DAILY.resolveDate("2026071", now) }
            .isInstanceOf(CoreException::class.java)
        assertThatThrownBy { RankingPeriod.HOURLY.resolveDate("20260717", now) }
            .isInstanceOf(CoreException::class.java)
        assertThat(RankingPeriod.DAILY.resolveDate("20260716", now)).isEqualTo("20260716")
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.domain.ranking.RankingPeriodTest"`
Expected: FAIL — 컴파일 에러

- [ ] **Step 3: RankingPeriod + port + Redis 구현**

`domain/ranking/RankingPeriod.kt`:

```kotlin
package com.loopers.domain.ranking

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

enum class RankingPeriod(val pattern: String, private val keyPrefix: String) {
    DAILY("yyyyMMdd", "ranking:all:v1:"),
    HOURLY("yyyyMMddHH", "ranking:hourly:v1:"),
    ;

    fun resolveDate(date: String?, now: ZonedDateTime): String {
        val formatter = DateTimeFormatter.ofPattern(pattern)
        if (date.isNullOrBlank()) return now.format(formatter)
        if (date.length != pattern.length) {
            throw CoreException(ErrorType.BAD_REQUEST, "date는 $pattern 형식이어야 합니다.")
        }
        runCatching { formatter.parse(date) }
            .getOrElse { throw CoreException(ErrorType.BAD_REQUEST, "date는 $pattern 형식이어야 합니다.") }
        return date
    }

    fun key(resolvedDate: String): String = keyPrefix + resolvedDate

    companion object {
        fun from(value: String?): RankingPeriod {
            if (value.isNullOrBlank()) return DAILY
            return entries.find { it.name.equals(value, ignoreCase = true) }
                ?: throw CoreException(ErrorType.BAD_REQUEST, "지원하지 않는 랭킹 기간입니다. (DAILY, HOURLY)")
        }
    }
}
```

`domain/ranking/RankingQueryRepository.kt`:

```kotlin
package com.loopers.domain.ranking

data class RankedProduct(
    val productId: Long,
    val score: Double,
)

interface RankingQueryRepository {
    /** score 내림차순으로 [offset, offset+size) 구간을 반환한다. */
    fun page(key: String, offset: Long, size: Long): List<RankedProduct>

    fun total(key: String): Long

    /** score 내림차순 0-based 순위. 미진입 시 null. */
    fun rank(key: String, productId: Long): Long?
}
```

`infrastructure/ranking/RankingRedisRepository.kt` — 조회 전용이라 `@Primary`(REPLICA_PREFERRED) 템플릿 사용, 랙 허용(스펙 §10):

```kotlin
package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.RankedProduct
import com.loopers.domain.ranking.RankingQueryRepository
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component

@Component
class RankingRedisRepository(
    private val redisTemplate: RedisTemplate<String, String>,
) : RankingQueryRepository {
    override fun page(key: String, offset: Long, size: Long): List<RankedProduct> {
        if (size <= 0) return emptyList()
        return redisTemplate.opsForZSet()
            .reverseRangeWithScores(key, offset, offset + size - 1)
            ?.mapNotNull { tuple ->
                val productId = tuple.value?.toLongOrNull() ?: return@mapNotNull null
                RankedProduct(productId = productId, score = tuple.score ?: 0.0)
            }
            ?: emptyList()
    }

    override fun total(key: String): Long = redisTemplate.opsForZSet().size(key) ?: 0L

    override fun rank(key: String, productId: Long): Long? =
        redisTemplate.opsForZSet().reverseRank(key, productId.toString())
}
```

- [ ] **Step 4: Redis 구현 통합 테스트**

```kotlin
package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.RankingQueryRepository
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate

@SpringBootTest
class RankingRedisRepositoryTest @Autowired constructor(
    private val repository: RankingQueryRepository,
    private val redisTemplate: RedisTemplate<String, String>,
    private val redisCleanUp: RedisCleanUp,
) {
    private val key = "ranking:all:v1:20260717"

    @BeforeEach
    fun seed() {
        redisTemplate.opsForZSet().add(key, "1", 5.0)
        redisTemplate.opsForZSet().add(key, "2", 3.0)
        redisTemplate.opsForZSet().add(key, "3", 1.0)
    }

    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
    }

    @DisplayName("page는 score 내림차순 구간을, total은 전체 수를 반환한다.")
    @Test
    fun pageAndTotal() {
        val page = repository.page(key, offset = 0, size = 2)
        assertThat(page.map { it.productId }).containsExactly(1L, 2L)
        assertThat(page[0].score).isEqualTo(5.0)
        assertThat(repository.total(key)).isEqualTo(3L)
        assertThat(repository.page(key, offset = 2, size = 2).map { it.productId }).containsExactly(3L)
    }

    @DisplayName("rank는 0-based 순위를, 미진입 상품·빈 키는 null/빈 목록을 반환한다.")
    @Test
    fun rankAndMisses() {
        assertThat(repository.rank(key, 1L)).isEqualTo(0L)
        assertThat(repository.rank(key, 3L)).isEqualTo(2L)
        assertThat(repository.rank(key, 999L)).isNull()
        assertThat(repository.page("ranking:all:v1:19990101", 0, 10)).isEmpty()
        assertThat(repository.total("ranking:all:v1:19990101")).isZero()
    }
}
```

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.domain.ranking.RankingPeriodTest" --tests "com.loopers.infrastructure.ranking.RankingRedisRepositoryTest"`
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
./gradlew ktlintFormat
git add -A && git commit -m "feat: 랭킹 조회 도메인 — RankingPeriod·ZREVRANGE 리포지토리 (R9-7)"
```

---

### Task 8: findActiveAllByIds + GetRankingsUsecase — 상품정보 aggregation (commerce-api)

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductRepository.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ProductRepositoryImpl.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ProductJpaRepository.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/ranking/RankingInfo.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/ranking/usecase/GetRankingsUsecase.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/application/ranking/GetRankingsUsecaseIntegrationTest.kt`

**Interfaces:**
- Consumes: `RankingPeriod`/`RankingQueryRepository`(Task 7), `BrandRepository.findActiveById(id): BrandModel?`(기존)
- Produces: `ProductRepository.findActiveAllByIds(ids: List<Long>): List<ProductModel>`; `RankingItemInfo(rank: Long, productId: Long, name: String, price: BigDecimal, brandName: String, likeCount: Int, score: Double)`; `RankingPageInfo(items: List<RankingItemInfo>, period: RankingPeriod, date: String, page: Int, size: Int, totalCount: Long)`; `GetRankingsUsecase.execute(Query(period, date, page, size)): RankingPageInfo` — page 1-based. Task 9가 사용.

- [ ] **Step 1: 실패하는 통합 테스트**

```kotlin
package com.loopers.application.ranking

import com.loopers.application.ranking.usecase.GetRankingsUsecase
import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.ranking.RankingPeriod
import com.loopers.support.error.CoreException
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@SpringBootTest
class GetRankingsUsecaseIntegrationTest @Autowired constructor(
    private val usecase: GetRankingsUsecase,
    private val brandRepository: BrandRepository,
    private val productRepository: ProductRepository,
    private val redisTemplate: RedisTemplate<String, String>,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
        databaseCleanUp.truncateAllTables()
    }

    private val today: String = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))

    private fun seedProduct(name: String, price: String): ProductModel {
        val brand = brandRepository.save(BrandModel(name = "brand-$name", description = "d"))
        return productRepository.save(
            ProductModel(brandId = brand.id, name = name, description = "d", price = BigDecimal(price)),
        )
    }

    @DisplayName("랭킹 페이지는 score 내림차순으로 상품정보가 aggregation되어 반환된다 — 1-based rank.")
    @Test
    fun returnsAggregatedRankingPage() {
        val first = seedProduct("first", "10000.00")
        val second = seedProduct("second", "20000.00")
        val key = "ranking:all:v1:$today"
        redisTemplate.opsForZSet().add(key, "${first.id}", 9.0)
        redisTemplate.opsForZSet().add(key, "${second.id}", 4.0)

        val result = usecase.execute(GetRankingsUsecase.Query(period = RankingPeriod.DAILY, date = null, page = 1, size = 20))

        assertThat(result.totalCount).isEqualTo(2L)
        assertThat(result.date).isEqualTo(today)
        assertThat(result.items).hasSize(2)
        assertThat(result.items[0].rank).isEqualTo(1L)
        assertThat(result.items[0].productId).isEqualTo(first.id)
        assertThat(result.items[0].name).isEqualTo("first")
        assertThat(result.items[0].brandName).isEqualTo("brand-first")
        assertThat(result.items[0].score).isEqualTo(9.0)
        assertThat(result.items[1].rank).isEqualTo(2L)
    }

    @DisplayName("2페이지는 offset 이후 구간을 rank 연속으로 반환한다.")
    @Test
    fun paginatesWithContinuousRank() {
        val key = "ranking:all:v1:$today"
        val products = (1..3).map { seedProduct("p$it", "1000.00") }
        products.forEachIndexed { index, p -> redisTemplate.opsForZSet().add(key, "${p.id}", 10.0 - index) }

        val result = usecase.execute(GetRankingsUsecase.Query(RankingPeriod.DAILY, null, page = 2, size = 2))

        assertThat(result.items).hasSize(1)
        assertThat(result.items[0].rank).isEqualTo(3L)
        assertThat(result.items[0].productId).isEqualTo(products[2].id)
    }

    @DisplayName("삭제(soft delete)된 상품은 aggregation에서 제외되고, 빈 키는 빈 목록을 반환한다.")
    @Test
    fun skipsDeletedProductAndHandlesEmptyKey() {
        val alive = seedProduct("alive", "1000.00")
        val deleted = seedProduct("deleted", "1000.00")
        deleted.softDelete()
        productRepository.save(deleted)
        val key = "ranking:all:v1:$today"
        redisTemplate.opsForZSet().add(key, "${alive.id}", 2.0)
        redisTemplate.opsForZSet().add(key, "${deleted.id}", 9.0)

        val result = usecase.execute(GetRankingsUsecase.Query(RankingPeriod.DAILY, null, page = 1, size = 20))
        assertThat(result.items.map { it.productId }).containsExactly(alive.id)

        val empty = usecase.execute(GetRankingsUsecase.Query(RankingPeriod.DAILY, "19990101", page = 1, size = 20))
        assertThat(empty.items).isEmpty()
        assertThat(empty.totalCount).isZero()
    }

    @DisplayName("page < 1, size 범위 밖은 BAD_REQUEST를 던진다.")
    @Test
    fun validatesPaging() {
        assertThatThrownBy { usecase.execute(GetRankingsUsecase.Query(RankingPeriod.DAILY, null, page = 0, size = 20)) }
            .isInstanceOf(CoreException::class.java)
        assertThatThrownBy { usecase.execute(GetRankingsUsecase.Query(RankingPeriod.DAILY, null, page = 1, size = 0)) }
            .isInstanceOf(CoreException::class.java)
        assertThatThrownBy { usecase.execute(GetRankingsUsecase.Query(RankingPeriod.DAILY, null, page = 1, size = 101)) }
            .isInstanceOf(CoreException::class.java)
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.ranking.GetRankingsUsecaseIntegrationTest"`
Expected: FAIL — 컴파일 에러

- [ ] **Step 3: 구현**

`ProductRepository.kt`에 추가:

```kotlin
fun findActiveAllByIds(ids: List<Long>): List<ProductModel>
```

`ProductJpaRepository.kt`에 추가:

```kotlin
fun findAllByIdInAndDeletedAtIsNull(ids: List<Long>): List<ProductModel>
```

`ProductRepositoryImpl.kt`에 추가 (기존 위임 스타일):

```kotlin
override fun findActiveAllByIds(ids: List<Long>): List<ProductModel> {
    if (ids.isEmpty()) return emptyList()
    return productJpaRepository.findAllByIdInAndDeletedAtIsNull(ids)
}
```

`application/ranking/RankingInfo.kt`:

```kotlin
package com.loopers.application.ranking

import com.loopers.domain.ranking.RankingPeriod
import java.math.BigDecimal

data class RankingItemInfo(
    val rank: Long,
    val productId: Long,
    val name: String,
    val price: BigDecimal,
    val brandName: String,
    val likeCount: Int,
    val score: Double,
)

data class RankingPageInfo(
    val items: List<RankingItemInfo>,
    val period: RankingPeriod,
    val date: String,
    val page: Int,
    val size: Int,
    val totalCount: Long,
)
```

`application/ranking/usecase/GetRankingsUsecase.kt`:

```kotlin
package com.loopers.application.ranking.usecase

import com.loopers.application.ranking.RankingItemInfo
import com.loopers.application.ranking.RankingPageInfo
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.ranking.RankingPeriod
import com.loopers.domain.ranking.RankingQueryRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

@Component
class GetRankingsUsecase(
    private val rankingQueryRepository: RankingQueryRepository,
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
) {
    @Transactional(readOnly = true)
    fun execute(query: Query): RankingPageInfo {
        if (query.page < 1) throw CoreException(ErrorType.BAD_REQUEST, "page는 1 이상이어야 합니다.")
        if (query.size !in 1..MAX_SIZE) throw CoreException(ErrorType.BAD_REQUEST, "size는 1~${MAX_SIZE}이어야 합니다.")

        val date = query.period.resolveDate(query.date, ZonedDateTime.now())
        val key = query.period.key(date)
        val offset = (query.page - 1L) * query.size

        val ranked = rankingQueryRepository.page(key, offset, query.size.toLong())
        val totalCount = rankingQueryRepository.total(key)
        val productsById = productRepository.findActiveAllByIds(ranked.map { it.productId }).associateBy { it.id }
        val brandNamesById = productsById.values.map { it.brandId }.distinct()
            .associateWith { brandRepository.findActiveById(it)?.name }

        // ZSET 순서 유지, 삭제된 상품은 스킵(스펙 §6 — 페이지 항목 수가 size보다 작아질 수 있음)
        val items = ranked.mapIndexedNotNull { index, rankedProduct ->
            val product = productsById[rankedProduct.productId] ?: return@mapIndexedNotNull null
            val brandName = brandNamesById[product.brandId] ?: return@mapIndexedNotNull null
            RankingItemInfo(
                rank = offset + index + 1,
                productId = product.id,
                name = product.name,
                price = product.price,
                brandName = brandName,
                likeCount = product.likeCount,
                score = rankedProduct.score,
            )
        }
        return RankingPageInfo(
            items = items,
            period = query.period,
            date = date,
            page = query.page,
            size = query.size,
            totalCount = totalCount,
        )
    }

    data class Query(
        val period: RankingPeriod,
        val date: String?,
        val page: Int,
        val size: Int,
    )

    companion object {
        private const val MAX_SIZE = 100
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.ranking.GetRankingsUsecaseIntegrationTest"`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
./gradlew ktlintFormat
git add -A && git commit -m "feat: 랭킹 페이지 usecase — 상품정보 aggregation·1-based 페이징 (R9-8)"
```

---

### Task 9: RankingV1Controller + Dto + E2E (commerce-api)

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/ranking/RankingV1Controller.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/ranking/RankingV1Dto.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/ranking/RankingV1ApiE2ETest.kt`

**Interfaces:**
- Consumes: `GetRankingsUsecase`(Task 8), `RankingPeriod.from`(Task 7)
- Produces: `GET /api/v1/rankings?period&date&page&size` → `ApiResponse<RankingV1Dto.RankingPageResponse>`

- [ ] **Step 1: 실패하는 E2E 테스트** (`ProductV1ApiE2ETest` 패턴 미러)

```kotlin
package com.loopers.interfaces.api.ranking

import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductRepository
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RankingV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val brandRepository: BrandRepository,
    private val productRepository: ProductRepository,
    private val redisTemplate: RedisTemplate<String, String>,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
        databaseCleanUp.truncateAllTables()
    }

    private val today: String = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))

    @DisplayName("GET /api/v1/rankings — 오늘 일간 랭킹이 상품정보와 함께 점수순으로 반환된다.")
    @Test
    fun returnsDailyRankings() {
        val brand = brandRepository.save(BrandModel(name = "Nike", description = "d"))
        val top = productRepository.save(ProductModel(brandId = brand.id, name = "top", description = "d", price = BigDecimal("10000.00")))
        val second = productRepository.save(ProductModel(brandId = brand.id, name = "second", description = "d", price = BigDecimal("20000.00")))
        redisTemplate.opsForZSet().add("ranking:all:v1:$today", "${top.id}", 9.0)
        redisTemplate.opsForZSet().add("ranking:all:v1:$today", "${second.id}", 4.0)

        val response = testRestTemplate.exchange(
            "/api/v1/rankings?size=20&page=1",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingPageResponse>>() {},
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        val data = response.body!!.data!!
        assertThat(data.date).isEqualTo(today)
        assertThat(data.items).hasSize(2)
        assertThat(data.items[0].rank).isEqualTo(1L)
        assertThat(data.items[0].name).isEqualTo("top")
        assertThat(data.items[0].brandName).isEqualTo("Nike")
        assertThat(data.totalCount).isEqualTo(2L)
    }

    @DisplayName("이전 날짜 date 파라미터로 어제 랭킹을 조회할 수 있다.")
    @Test
    fun returnsPastDateRankings() {
        val brand = brandRepository.save(BrandModel(name = "Nike", description = "d"))
        val p = productRepository.save(ProductModel(brandId = brand.id, name = "yesterday", description = "d", price = BigDecimal("10000.00")))
        val yesterday = ZonedDateTime.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        redisTemplate.opsForZSet().add("ranking:all:v1:$yesterday", "${p.id}", 1.0)

        val response = testRestTemplate.exchange(
            "/api/v1/rankings?date=$yesterday",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingPageResponse>>() {},
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body!!.data!!.items.map { it.productId }).containsExactly(p.id)
    }

    @DisplayName("period=HOURLY는 시간 키를 조회한다.")
    @Test
    fun returnsHourlyRankings() {
        val brand = brandRepository.save(BrandModel(name = "Nike", description = "d"))
        val p = productRepository.save(ProductModel(brandId = brand.id, name = "hot", description = "d", price = BigDecimal("10000.00")))
        val hour = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHH"))
        redisTemplate.opsForZSet().add("ranking:hourly:v1:$hour", "${p.id}", 2.0)

        val response = testRestTemplate.exchange(
            "/api/v1/rankings?period=HOURLY",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingPageResponse>>() {},
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body!!.data!!.items.map { it.productId }).containsExactly(p.id)
    }

    @DisplayName("잘못된 date/period는 400 BAD_REQUEST를 반환한다.")
    @Test
    fun rejectsInvalidParams() {
        val badDate = testRestTemplate.exchange(
            "/api/v1/rankings?date=2026-07-17",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<ApiResponse<Any>>() {},
        )
        assertThat(badDate.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(badDate.body!!.meta.errorCode).isEqualTo(ErrorType.BAD_REQUEST.code)

        val badPeriod = testRestTemplate.exchange(
            "/api/v1/rankings?period=WEEKLY",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<ApiResponse<Any>>() {},
        )
        assertThat(badPeriod.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.ranking.RankingV1ApiE2ETest"`
Expected: FAIL — 컴파일 에러(Dto/Controller 미존재)

- [ ] **Step 3: 구현**

`RankingV1Dto.kt`:

```kotlin
package com.loopers.interfaces.api.ranking

import com.loopers.application.ranking.RankingItemInfo
import com.loopers.application.ranking.RankingPageInfo
import java.math.BigDecimal

class RankingV1Dto {
    data class RankingPageResponse(
        val items: List<RankingItemResponse>,
        val period: String,
        val date: String,
        val page: Int,
        val size: Int,
        val totalCount: Long,
    ) {
        companion object {
            fun from(info: RankingPageInfo): RankingPageResponse {
                return RankingPageResponse(
                    items = info.items.map { RankingItemResponse.from(it) },
                    period = info.period.name,
                    date = info.date,
                    page = info.page,
                    size = info.size,
                    totalCount = info.totalCount,
                )
            }
        }
    }

    data class RankingItemResponse(
        val rank: Long,
        val productId: Long,
        val name: String,
        val price: BigDecimal,
        val brandName: String,
        val likeCount: Int,
        val score: Double,
    ) {
        companion object {
            fun from(info: RankingItemInfo): RankingItemResponse {
                return RankingItemResponse(
                    rank = info.rank,
                    productId = info.productId,
                    name = info.name,
                    price = info.price,
                    brandName = info.brandName,
                    likeCount = info.likeCount,
                    score = info.score,
                )
            }
        }
    }
}
```

`RankingV1Controller.kt`:

```kotlin
package com.loopers.interfaces.api.ranking

import com.loopers.application.ranking.usecase.GetRankingsUsecase
import com.loopers.domain.ranking.RankingPeriod
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/rankings")
class RankingV1Controller(
    private val getRankingsUsecase: GetRankingsUsecase,
) {
    @GetMapping
    fun getRankings(
        @RequestParam(required = false) period: String?,
        @RequestParam(required = false) date: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<RankingV1Dto.RankingPageResponse> {
        return getRankingsUsecase.execute(
            GetRankingsUsecase.Query(
                period = RankingPeriod.from(period),
                date = date,
                page = page,
                size = size,
            ),
        ).let { RankingV1Dto.RankingPageResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.ranking.RankingV1ApiE2ETest"`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
./gradlew ktlintFormat
git add -A && git commit -m "feat: 랭킹 Page API — GET /api/v1/rankings (period·date·1-based page) (R9-9)"
```

---

### Task 10: 상품 상세 rank 필드 (commerce-api)

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/product/ProductInfo.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/product/usecase/GetProductDetailUsecase.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/product/ProductV1Dto.kt` (`ProductResponse` + `from`)
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/ProductV1ApiE2ETest.kt` (테스트 추가)

**Interfaces:**
- Consumes: `RankingQueryRepository.rank(key, productId)`(Task 7, 0-based), `RankingPeriod.DAILY`
- Produces: `ProductInfo.rank: Long?`(1-based, 미진입 null), `ProductV1Dto.ProductResponse.rank: Long?`

- [ ] **Step 1: 실패하는 E2E 테스트 — `ProductV1ApiE2ETest`에 추가**

기존 파일의 상품 상세 조회 테스트 그룹에 추가 (기존 시딩 헬퍼 재사용, `RedisTemplate`·오늘 날짜 포맷 필드가 없으면 이 테스트와 함께 주입 추가):

```kotlin
@DisplayName("상품 상세 조회 시 오늘 일간 랭킹 순위(1-based)가 함께 반환된다.")
@Test
fun returnsProductDetailWithRank() {
    val brand = brandRepository.save(BrandModel(name = "Nike", description = "Shoes"))
    val ranked = productRepository.save(ProductModel(brandId = brand.id, name = "ranked", description = "d", price = BigDecimal("10000.00")))
    val other = productRepository.save(ProductModel(brandId = brand.id, name = "other", description = "d", price = BigDecimal("10000.00")))
    val today = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
    redisTemplate.opsForZSet().add("ranking:all:v1:$today", "${other.id}", 9.0)
    redisTemplate.opsForZSet().add("ranking:all:v1:$today", "${ranked.id}", 4.0)

    val response = testRestTemplate.exchange(
        "/api/v1/products/${ranked.id}",
        HttpMethod.GET,
        null,
        object : ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>>() {},
    )

    assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
    assertThat(response.body!!.data!!.rank).isEqualTo(2L)
}

@DisplayName("랭킹에 없는 상품의 상세 조회는 rank=null을 반환한다.")
@Test
fun returnsNullRankWhenUnranked() {
    val brand = brandRepository.save(BrandModel(name = "Nike", description = "Shoes"))
    val product = productRepository.save(ProductModel(brandId = brand.id, name = "unranked", description = "d", price = BigDecimal("10000.00")))

    val response = testRestTemplate.exchange(
        "/api/v1/products/${product.id}",
        HttpMethod.GET,
        null,
        object : ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>>() {},
    )

    assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
    assertThat(response.body!!.data!!.rank).isNull()
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.ProductV1ApiE2ETest"`
Expected: FAIL — `rank` 프로퍼티 미존재(컴파일 에러)

- [ ] **Step 3: 구현**

`ProductInfo.kt` — 필드 추가(캐시된 구버전 JSON과 호환되도록 기본값 null):

```kotlin
data class ProductInfo(
    val id: Long,
    val brand: Brand,
    val name: String,
    val description: String,
    val price: BigDecimal,
    val stockQuantity: Int,
    val likeCount: Int,
    val rank: Long? = null,
)
```

(`from()`은 변경 없음 — rank는 usecase가 `.copy()`로 부착)

`GetProductDetailUsecase.kt` — 캐시 히트/미스 **두 경로 모두** rank를 부착하도록 단일 출구로 재구성. `RankingQueryRepository` 주입 추가:

```kotlin
@Component
class GetProductDetailUsecase(
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
    private val productStockRepository: ProductStockRepository,
    private val productCacheRepository: ProductCacheRepository,
    private val rankingQueryRepository: RankingQueryRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(GetProductDetailUsecase::class.java)
    private val productCatalogDomainService = ProductCatalogDomainService()

    @Transactional(readOnly = true)
    fun execute(productId: Long): ProductInfo {
        val info = loadProductInfo(productId)
        eventPublisher.publishEvent(ProductViewedEvent(productId = productId))
        // rank는 실시간 값 — 캐시(ProductInfo TTL 60s)와 무관하게 매 요청 조회, 장애 시 null(스펙 §7)
        return info.copy(rank = currentDailyRank(productId))
    }

    private fun loadProductInfo(productId: Long): ProductInfo {
        runCatching { productCacheRepository.getDetail(productId) }
            .onFailure { log.warn("Failed to get product detail cache. productId={}", productId, it) }
            .getOrNull()
            ?.let { return it }

        val product = productRepository.findActiveById(productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.")
        val brand = brandRepository.findActiveById(product.brandId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.")
        val stockQuantity = productStockRepository.findByProductId(productId)?.quantity ?: 0

        val productInfo = productCatalogDomainService.getDetail(product = product, brand = brand)
            .let { ProductInfo.from(it, stockQuantity) }
        runCatching { productCacheRepository.putDetail(productId = productId, product = productInfo) }
            .onFailure { log.warn("Failed to put product detail cache. productId={}", productId, it) }
        return productInfo
    }

    private fun currentDailyRank(productId: Long): Long? =
        runCatching {
            val date = RankingPeriod.DAILY.resolveDate(null, ZonedDateTime.now())
            rankingQueryRepository.rank(RankingPeriod.DAILY.key(date), productId)?.plus(1)
        }.onFailure { log.warn("Failed to get product rank. productId={}", productId, it) }
            .getOrNull()
}
```

import 추가: `com.loopers.domain.ranking.RankingPeriod`, `com.loopers.domain.ranking.RankingQueryRepository`, `java.time.ZonedDateTime`

`ProductV1Dto.kt` — `ProductResponse`에 `val rank: Long?` 추가, `from(info)`에 `rank = info.rank` 추가.

- [ ] **Step 4: 통과 확인 + 상품 회귀**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.ProductV1ApiE2ETest" --tests "com.loopers.application.product.*"`
Expected: PASS — rank 테스트 2개 + 기존 상품 테스트 전부(rank 기본 null이라 기존 단언 영향 없음)

- [ ] **Step 5: Commit**

```bash
./gradlew ktlintFormat
git add -A && git commit -m "feat: 상품 상세에 오늘 일간 랭킹 순위 부착 — 미진입·장애 시 null (R9-10)"
```

---

### Task 11: .http 예시 + 문서 + 전체 검증

**Files:**
- Create: `http/commerce-api/ranking-v1.http`
- Modify: `docs/superpowers/specs/2026-07-17-round9-ranking-design.md` (구현 중 결정 변경 시 변경 이력 추가)

- [ ] **Step 1: .http 작성**

```
### 오늘 일간 랭킹 Top 20
GET http://localhost:8080/api/v1/rankings?size=20&page=1

### 특정 날짜 일간 랭킹
GET http://localhost:8080/api/v1/rankings?date=20260716&size=20&page=1

### 시간 단위 랭킹 (현재 시각)
GET http://localhost:8080/api/v1/rankings?period=HOURLY&size=20&page=1

### 시간 단위 랭킹 (특정 시각)
GET http://localhost:8080/api/v1/rankings?period=HOURLY&date=2026071714&size=20&page=1

### 상품 상세 (rank 포함)
GET http://localhost:8080/api/v1/products/1
```

- [ ] **Step 2: 전체 테스트 + 린트**

Run: `./gradlew ktlintCheck :apps:commerce-api:test :apps:commerce-streamer:test`
Expected: PASS 전부. E2E 흐름(발행→ZSET→API) 체크리스트는 Task 6(발행→ZSET) + Task 9/10(ZSET→API) 테스트 조합으로 커버 — 두 앱이 분리 배포라 단일 JVM 테스트로는 교차 불가(스펙 §8).

- [ ] **Step 3: 스펙 변경 이력 확인**

구현 중 스펙과 달라진 결정이 있으면 spec 파일 상단 `변경 이력`에 한 줄 추가. 없으면 생략.

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "docs: 랭킹 API .http 예시 추가 (R9-11)"
```

이후: PR 생성은 별도 단계 — `gh pr create --repo loopers-labs/loop-pack-be-l2-vol4-kotlin --base yeonjoo7 --head yeonjoo7:volume-9`, 제목 `[volume-9] Redis ZSET 실시간 상품 랭킹 — 별도 컨슈머 그룹 · SETNX 멱등 · log10 가중치`, 본문은 PR 템플릿 3섹션(결정별 대안 A/B → 결정 → 트레이드오프).
