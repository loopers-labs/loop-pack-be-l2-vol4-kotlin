# Round 8 — Redis 주문 대기열 (Virtual Waiting Room) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`) syntax.

**Goal:** 주문 API 앞단에 Redis Sorted Set 기반 대기열 + 입장 토큰(용량 기반 leaky bucket 스케줄러) + 폴링 순번/예상대기 조회를 붙여, 트래픽 폭증 시 back-pressure로 시스템을 보호하고 유저에게 공정한 대기 경험을 제공한다.

**Architecture:** `…:waiting:v1` ZSet(member=userId, score=진입ms)로 FIFO 대기. `QueuePromoteScheduler`가 100ms마다 `admit=min(rateBatch, capacity−active)`명을 `ZPOPMIN`해 입장 토큰(String, TTL 5분) 발급, `…:processing:v1` ZSet으로 active 추적(만료 프룬). 주문 엔드포인트는 `X-Entry-Token` 헤더 + `EntryTokenGate`로 검증(Redis 장애 시 fail-open bypass) 후 `CreateOrderUsecase`(불변) 호출, 완료 시 토큰 소비. 주문 이후 흐름은 R7 파이프라인 그대로.

**Tech Stack:** Kotlin 2.0.20, Spring Boot 3.4.4, Spring Data Redis(Lettuce, `RedisTemplate<String,String>` `@Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)`, `opsForZSet`/`opsForValue`), `@Scheduled`, Redis Testcontainers, JUnit5+AssertJ, `runConcurrently`.

## Global Constraints

- 레이어: `domain/queue`(포트), `infrastructure/queue`(Redis repo + 스케줄러), `application/queue`(usecase/gate), `interfaces/api/queue`(controller/dto). 의존 방향 `interfaces→application→domain`, infra는 port 구현.
- Redis 접근: `@Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) redisTemplate: RedisTemplate<String,String>`(마스터, 쓰기+즉시읽기). member/score는 String/Double. 키 컨벤션 `commerce-api:queue:order:<name>:v1[:…]`.
- **큐 repo 프리미티브는 Redis 예외를 삼키지 않고 전파**한다(캐시 repo와 다름). Fail-open degradation 정책은 `EntryTokenGate`/`GetQueuePositionUsecase`가 소유(Redis 장애를 "토큰 없음"과 구분해야 함).
- 산정값(프로퍼티): `queue.capacity=50`(DB 풀=동시 상한), `queue.rate-batch=18`(100ms당 발급 상한, Thundering Herd 스프레드), `queue.token-ttl-seconds=300`(5분). `queue.promoter.scheduler.enabled`(테스트 false).
- 스케줄러 로직은 `now`를 파라미터로 받아 결정적 테스트 가능. `@ConditionalOnProperty(matchIfMissing=true)`.
- `CreateOrderUsecase`/기존 주문 도메인 불변. ktlint(≤130). 커밋 말미 `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.

---

### Task 1: 대기열 Redis 자료구조 + 리포지토리 (enter/rank/total)

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/queue/OrderQueueRepository.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/queue/OrderQueueRedisRepository.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/queue/OrderQueueRedisRepositoryTest.kt`

**Interfaces:**
- Produces: `OrderQueueRepository.enter(userId: Long, nowMillis: Long): Long`(1-based 순번; 중복 진입은 기존 순번 유지), `rank(userId: Long): Long?`(0-based, 미대기 시 null), `total(): Long`. 이후 태스크에서 token/processing 프리미티브를 같은 인터페이스에 추가.

- [ ] **Step 1: 실패 테스트 작성** — `OrderQueueRedisRepositoryTest.kt` (Redis Testcontainer):

```kotlin
package com.loopers.infrastructure.queue

import com.loopers.domain.queue.OrderQueueRepository
import com.loopers.support.runConcurrently
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.concurrent.atomic.AtomicLong

@SpringBootTest
class OrderQueueRedisRepositoryTest {
    @Autowired lateinit var repository: OrderQueueRepository
    @Autowired lateinit var redisCleanUp: RedisCleanUp

    @AfterEach fun tearDown() = redisCleanUp.truncateAll()

    @DisplayName("진입 순서대로 순번이 부여되고, 전체 대기 인원이 집계된다.")
    @Test
    fun entersInOrder() {
        assertThat(repository.enter(userId = 1L, nowMillis = 1000)).isEqualTo(1L)
        assertThat(repository.enter(userId = 2L, nowMillis = 1001)).isEqualTo(2L)
        assertThat(repository.rank(1L)).isEqualTo(0L)
        assertThat(repository.rank(2L)).isEqualTo(1L)
        assertThat(repository.total()).isEqualTo(2L)
    }

    @DisplayName("같은 userId가 재진입해도 순번과 인원은 유지된다(중복 방지).")
    @Test
    fun idempotentReentry() {
        repository.enter(userId = 1L, nowMillis = 1000)
        val second = repository.enter(userId = 1L, nowMillis = 5000)
        assertThat(second).isEqualTo(1L)
        assertThat(repository.total()).isEqualTo(1L)
    }

    @DisplayName("미대기 유저의 rank는 null이다.")
    @Test
    fun rankNullWhenNotWaiting() {
        assertThat(repository.rank(99L)).isNull()
    }

    @DisplayName("동시에 N명이 진입해도 전체 인원은 정확히 N, 순번은 0..N-1로 유일하다.")
    @Test
    fun concurrentEnter() {
        val n = 200
        val seq = AtomicLong(1_000_000)
        runConcurrently(threadCount = n) { i ->
            repository.enter(userId = (i + 1).toLong(), nowMillis = seq.incrementAndGet())
        }
        assertThat(repository.total()).isEqualTo(n.toLong())
        val ranks = (0 until n).map { repository.rank((it + 1).toLong()) }
        assertThat(ranks.filterNotNull().toSet()).hasSize(n) // 모든 rank 유일
    }
}
```

- [ ] **Step 2: 실패 확인** — `./gradlew :apps:commerce-api:test --tests "com.loopers.infrastructure.queue.OrderQueueRedisRepositoryTest"` → FAIL(미해결).

- [ ] **Step 3: 구현**

`domain/queue/OrderQueueRepository.kt`:
```kotlin
package com.loopers.domain.queue

interface OrderQueueRepository {
    /** 대기열 진입(중복 시 기존 순번 유지). 반환: 1-based 순번. */
    fun enter(userId: Long, nowMillis: Long): Long

    /** 0-based 순번. 대기 중이 아니면 null. */
    fun rank(userId: Long): Long?

    /** 전체 대기 인원(ZCARD). */
    fun total(): Long
}
```

`infrastructure/queue/OrderQueueRedisRepository.kt` (프리미티브는 예외 전파 — 삼키지 않음):
```kotlin
package com.loopers.infrastructure.queue

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.queue.OrderQueueRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component

@Component
class OrderQueueRedisRepository(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
) : OrderQueueRepository {
    private val zset get() = redisTemplate.opsForZSet()

    override fun enter(userId: Long, nowMillis: Long): Long {
        // ZADD NX: 이미 있으면 score(순번) 보존
        zset.addIfAbsent(WAITING_KEY, userId.toString(), nowMillis.toDouble())
        return (rank(userId) ?: 0L) + 1L
    }

    override fun rank(userId: Long): Long? = zset.rank(WAITING_KEY, userId.toString())

    override fun total(): Long = zset.size(WAITING_KEY) ?: 0L

    companion object {
        const val WAITING_KEY = "commerce-api:queue:order:waiting:v1"
    }
}
```

- [ ] **Step 4: 통과 확인** — Docker(Redis 컨테이너). test PASS.

- [ ] **Step 5: 커밋**
```bash
./gradlew :apps:commerce-api:ktlintCheck -q
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/queue/OrderQueueRepository.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/queue/OrderQueueRedisRepository.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/queue/OrderQueueRedisRepositoryTest.kt
git commit -m "feat: redis sorted-set order waiting queue (enter/rank/total) (R8-1)"
```

---

### Task 2: 대기열 진입/순번 API (EnterQueue/GetQueuePosition + Controller)

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/queue/usecase/EnterQueueUsecase.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/queue/usecase/GetQueuePositionUsecase.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/queue/QueuePosition.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/queue/QueueV1Controller.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/queue/QueueV1Dto.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/application/queue/QueueUsecaseIntegrationTest.kt`

**Interfaces:**
- Consumes: `OrderQueueRepository`(T1), `UserService.getProfile(loginId,password).id`.
- Produces: `EnterQueueUsecase.execute(loginId, password): Long`(순번), `GetQueuePositionUsecase.execute(loginId, password): QueuePosition`(`data class QueuePosition(position: Long?, waiting: Boolean)` — 이번 태스크는 순번만; 예상대기/토큰은 Task 6에서 확장). 미진입 시 `position=null, waiting=false`.

- [ ] **Step 1: 실패 테스트 작성** — `QueueUsecaseIntegrationTest.kt` (유저 셋업은 기존 통합테스트 방식 재사용):
```kotlin
package com.loopers.application.queue

import com.loopers.application.queue.usecase.EnterQueueUsecase
import com.loopers.application.queue.usecase.GetQueuePositionUsecase
import com.loopers.domain.user.UserService
import com.loopers.utils.RedisCleanUp
// user signup 은 기존 통합테스트(IssueCouponUsecaseTest 등)와 동일 방식
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class QueueUsecaseIntegrationTest {
    @Autowired lateinit var enterQueue: EnterQueueUsecase
    @Autowired lateinit var getPosition: GetQueuePositionUsecase
    @Autowired lateinit var userService: UserService
    @Autowired lateinit var redisCleanUp: RedisCleanUp

    @AfterEach fun tearDown() = redisCleanUp.truncateAll()

    @DisplayName("대기열에 진입하면 순번이 부여되고, 순번 조회가 일치한다.")
    @Test
    fun enterAndPosition() {
        // arrange: 활성 유저 2명 생성 (기존 signup 방식)
        // val (a, b) = ...

        val posA = enterQueue.execute(a.loginId, a.rawPassword)
        val posB = enterQueue.execute(b.loginId, b.rawPassword)

        assertThat(posA).isEqualTo(1L)
        assertThat(posB).isEqualTo(2L)
        assertThat(getPosition.execute(a.loginId, a.rawPassword).position).isEqualTo(1L)
        assertThat(getPosition.execute(a.loginId, a.rawPassword).waiting).isTrue()
    }

    @DisplayName("미진입 유저의 순번 조회는 waiting=false, position=null.")
    @Test
    fun positionWhenNotEntered() {
        // arrange: 유저 c 생성 (진입 X)
        val result = getPosition.execute(c.loginId, c.rawPassword)
        assertThat(result.waiting).isFalse()
        assertThat(result.position).isNull()
    }
}
```
> 유저 생성/로그인 방식은 이 모듈 기존 통합테스트(`IssueCouponUsecaseTest`, `OrderCouponIntegrationTest`)를 그대로 따른다.

- [ ] **Step 2: 실패 확인** — `--tests "com.loopers.application.queue.QueueUsecaseIntegrationTest"` → FAIL.

- [ ] **Step 3: 구현**

`application/queue/QueuePosition.kt`:
```kotlin
package com.loopers.application.queue

data class QueuePosition(
    val position: Long?,   // 1-based, 미대기 시 null
    val waiting: Boolean,
)
```

`application/queue/usecase/EnterQueueUsecase.kt`:
```kotlin
package com.loopers.application.queue.usecase

import com.loopers.domain.queue.OrderQueueRepository
import com.loopers.domain.user.UserService
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class EnterQueueUsecase(
    private val userService: UserService,
    private val queueRepository: OrderQueueRepository,
) {
    fun execute(loginId: String, password: String): Long {
        val user = userService.getProfile(loginId = loginId, password = password)
        return queueRepository.enter(userId = user.id, nowMillis = Instant.now().toEpochMilli())
    }
}
```

`application/queue/usecase/GetQueuePositionUsecase.kt`:
```kotlin
package com.loopers.application.queue.usecase

import com.loopers.application.queue.QueuePosition
import com.loopers.domain.queue.OrderQueueRepository
import com.loopers.domain.user.UserService
import org.springframework.stereotype.Component

@Component
class GetQueuePositionUsecase(
    private val userService: UserService,
    private val queueRepository: OrderQueueRepository,
) {
    fun execute(loginId: String, password: String): QueuePosition {
        val user = userService.getProfile(loginId = loginId, password = password)
        val rank = queueRepository.rank(user.id)
        return if (rank != null) {
            QueuePosition(position = rank + 1, waiting = true)
        } else {
            QueuePosition(position = null, waiting = false)
        }
    }
}
```

`interfaces/api/queue/QueueV1Dto.kt`:
```kotlin
package com.loopers.interfaces.api.queue

import com.loopers.application.queue.QueuePosition

class QueueV1Dto {
    data class EnterResponse(val position: Long)

    data class PositionResponse(val position: Long?, val waiting: Boolean) {
        companion object {
            fun from(p: QueuePosition) = PositionResponse(position = p.position, waiting = p.waiting)
        }
    }
}
```

`interfaces/api/queue/QueueV1Controller.kt`:
```kotlin
package com.loopers.interfaces.api.queue

import com.loopers.application.queue.usecase.EnterQueueUsecase
import com.loopers.application.queue.usecase.GetQueuePositionUsecase
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/queue")
class QueueV1Controller(
    private val enterQueueUsecase: EnterQueueUsecase,
    private val getQueuePositionUsecase: GetQueuePositionUsecase,
) {
    @PostMapping("/enter")
    fun enter(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") password: String,
    ): ApiResponse<QueueV1Dto.EnterResponse> =
        enterQueueUsecase.execute(loginId, password)
            .let { ApiResponse.success(QueueV1Dto.EnterResponse(position = it)) }

    @GetMapping("/position")
    fun position(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") password: String,
    ): ApiResponse<QueueV1Dto.PositionResponse> =
        getQueuePositionUsecase.execute(loginId, password)
            .let { ApiResponse.success(QueueV1Dto.PositionResponse.from(it)) }
}
```

- [ ] **Step 4: 통과 확인** — test PASS.

- [ ] **Step 5: 커밋**
```bash
./gradlew :apps:commerce-api:ktlintCheck -q
git add apps/commerce-api/src/main/kotlin/com/loopers/application/queue/ \
        apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/queue/ \
        apps/commerce-api/src/test/kotlin/com/loopers/application/queue/QueueUsecaseIntegrationTest.kt
git commit -m "feat: queue enter/position API (R8-1)"
```

---

### Task 3: 입장 토큰 + processing 프리미티브 (repo 확장)

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/queue/OrderQueueRepository.kt` (프리미티브 추가)
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/queue/OrderQueueRedisRepository.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/queue/OrderQueueTokenRepositoryTest.kt`

**Interfaces:**
- Produces (port 추가): `pruneExpiredProcessing(beforeMillis: Long)`, `countActive(): Long`, `popNext(count: Int): List<Long>`(ZPOPMIN → userIds), `issueToken(userId: Long, token: String, ttlSeconds: Long, nowMillis: Long)`(SET token EX + ZADD processing), `findToken(userId: Long): String?`, `consume(userId: Long)`(DEL token + ZREM processing).

- [ ] **Step 1: 실패 테스트 작성** — `OrderQueueTokenRepositoryTest.kt`:
```kotlin
package com.loopers.infrastructure.queue

import com.loopers.domain.queue.OrderQueueRepository
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class OrderQueueTokenRepositoryTest {
    @Autowired lateinit var repository: OrderQueueRepository
    @Autowired lateinit var redisCleanUp: RedisCleanUp

    @AfterEach fun tearDown() = redisCleanUp.truncateAll()

    @DisplayName("토큰 발급 후 조회되고, active(processing)에 집계되며, 소비하면 사라진다.")
    @Test
    fun issueFindConsume() {
        repository.issueToken(userId = 1L, token = "tok-1", ttlSeconds = 300, nowMillis = 1000)
        assertThat(repository.findToken(1L)).isEqualTo("tok-1")
        assertThat(repository.countActive()).isEqualTo(1L)

        repository.consume(1L)
        assertThat(repository.findToken(1L)).isNull()
        assertThat(repository.countActive()).isEqualTo(0L)
    }

    @DisplayName("popNext는 앞에서 N명을 원자적으로 꺼낸다.")
    @Test
    fun popNext() {
        repository.enter(1L, 1000); repository.enter(2L, 1001); repository.enter(3L, 1002)
        assertThat(repository.popNext(2)).containsExactly(1L, 2L)
        assertThat(repository.total()).isEqualTo(1L)
    }

    @DisplayName("pruneExpiredProcessing은 임계 이전 발급분을 회수한다(만료 자리 반환).")
    @Test
    fun prune() {
        repository.issueToken(1L, "a", 300, nowMillis = 1000)   // score=1000
        repository.issueToken(2L, "b", 300, nowMillis = 9000)   // score=9000
        repository.pruneExpiredProcessing(beforeMillis = 5000)   // 1000 < 5000 → 회수
        assertThat(repository.countActive()).isEqualTo(1L)
    }
}
```

- [ ] **Step 2: 실패 확인** — `--tests "com.loopers.infrastructure.queue.OrderQueueTokenRepositoryTest"` → FAIL.

- [ ] **Step 3: 구현** — port에 6개 메서드 추가, repo 구현 추가:
```kotlin
// OrderQueueRepository.kt 에 추가:
    fun pruneExpiredProcessing(beforeMillis: Long)
    fun countActive(): Long
    fun popNext(count: Int): List<Long>
    fun issueToken(userId: Long, token: String, ttlSeconds: Long, nowMillis: Long)
    fun findToken(userId: Long): String?
    fun consume(userId: Long)
```
```kotlin
// OrderQueueRedisRepository.kt 에 추가 (companion 키/기존 zset 활용):
import java.time.Duration

    override fun pruneExpiredProcessing(beforeMillis: Long) {
        zset.removeRangeByScore(PROCESSING_KEY, Double.NEGATIVE_INFINITY, beforeMillis.toDouble())
    }

    override fun countActive(): Long = zset.size(PROCESSING_KEY) ?: 0L

    override fun popNext(count: Int): List<Long> {
        if (count <= 0) return emptyList()
        return zset.popMin(WAITING_KEY, count.toLong())
            ?.mapNotNull { it.value?.toLong() }
            ?: emptyList()
    }

    override fun issueToken(userId: Long, token: String, ttlSeconds: Long, nowMillis: Long) {
        redisTemplate.opsForValue().set(tokenKey(userId), token, Duration.ofSeconds(ttlSeconds))
        zset.add(PROCESSING_KEY, userId.toString(), nowMillis.toDouble())
    }

    override fun findToken(userId: Long): String? = redisTemplate.opsForValue().get(tokenKey(userId))

    override fun consume(userId: Long) {
        redisTemplate.delete(tokenKey(userId))
        zset.remove(PROCESSING_KEY, userId.toString())
    }
    // companion object 에 추가:
    //   const val PROCESSING_KEY = "commerce-api:queue:order:processing:v1"
    //   fun tokenKey(userId: Long) = "commerce-api:queue:order:token:v1:$userId"
```

- [ ] **Step 4: 통과 확인** — test PASS.

- [ ] **Step 5: 커밋**
```bash
./gradlew :apps:commerce-api:ktlintCheck -q
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/queue/OrderQueueRepository.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/queue/OrderQueueRedisRepository.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/queue/OrderQueueTokenRepositoryTest.kt
git commit -m "feat: entry token + processing primitives (issue/find/consume/pop/prune) (R8-2)"
```

---

### Task 4: Leaky bucket 프로모트 usecase + 스케줄러

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/queue/usecase/PromoteQueueUsecase.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/queue/QueuePromoteScheduler.kt`
- Modify: `apps/commerce-api/src/main/resources/application.yml` (`local, test` 프로파일에 `queue.promoter.scheduler.enabled: false`)
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/application/queue/PromoteQueueUsecaseIntegrationTest.kt`

**Interfaces:**
- Consumes: `OrderQueueRepository`(전체).
- Produces: `PromoteQueueUsecase.promoteOnce(nowMillis: Long): Int`(발급 수). 스케줄러 `QueuePromoteScheduler`가 `@Scheduled(fixedDelay=100)`로 호출.

- [ ] **Step 1: 실패 테스트 작성** — `PromoteQueueUsecaseIntegrationTest.kt` (capacity/rateBatch를 작게 프로퍼티 오버라이드해 결정적으로):
```kotlin
package com.loopers.application.queue

import com.loopers.application.queue.usecase.PromoteQueueUsecase
import com.loopers.domain.queue.OrderQueueRepository
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

@TestPropertySource(properties = ["queue.capacity=5", "queue.rate-batch=3", "queue.token-ttl-seconds=300"])
@SpringBootTest
class PromoteQueueUsecaseIntegrationTest {
    @Autowired lateinit var promote: PromoteQueueUsecase
    @Autowired lateinit var repository: OrderQueueRepository
    @Autowired lateinit var redisCleanUp: RedisCleanUp

    @AfterEach fun tearDown() = redisCleanUp.truncateAll()

    @DisplayName("한 tick은 min(rateBatch, capacity-active)명만 발급하고 나머지는 대기 유지한다.")
    @Test
    fun admitsRateBatch() {
        (1L..10L).forEach { repository.enter(it, 1000 + it) }
        val issued = promote.promoteOnce(nowMillis = 2000)   // min(3, 5-0)=3
        assertThat(issued).isEqualTo(3)
        assertThat(repository.total()).isEqualTo(7L)          // 10-3 대기 유지
        assertThat(repository.findToken(1L)).isNotNull()
    }

    @DisplayName("capacity가 차면 발급하지 않고, active가 줄면(소비) 다음 tick에 다시 발급한다.")
    @Test
    fun capacityBound() {
        (1L..10L).forEach { repository.enter(it, 1000 + it) }
        promote.promoteOnce(2000)   // 3 발급 (active=3)
        promote.promoteOnce(2100)   // min(3, 5-3)=2 발급 (active=5)
        assertThat(promote.promoteOnce(2200)).isEqualTo(0)   // capacity full → 0
        repository.consume(1L)                                // active=4
        assertThat(promote.promoteOnce(2300)).isEqualTo(1)   // min(3, 5-4)=1
    }
}
```

- [ ] **Step 2: 실패 확인** — `--tests "com.loopers.application.queue.PromoteQueueUsecaseIntegrationTest"` → FAIL.

- [ ] **Step 3: 구현**

`application/queue/usecase/PromoteQueueUsecase.kt`:
```kotlin
package com.loopers.application.queue.usecase

import com.loopers.domain.queue.OrderQueueRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class PromoteQueueUsecase(
    private val queueRepository: OrderQueueRepository,
    @Value("\${queue.capacity:50}") private val capacity: Long,
    @Value("\${queue.rate-batch:18}") private val rateBatch: Int,
    @Value("\${queue.token-ttl-seconds:300}") private val ttlSeconds: Long,
) {
    // 용량 기반 leaky bucket 1 tick. 만료 프룬 → active 산정 → min(rateBatch, capacity-active) 발급.
    fun promoteOnce(nowMillis: Long): Int {
        queueRepository.pruneExpiredProcessing(beforeMillis = nowMillis - ttlSeconds * 1000)
        val active = queueRepository.countActive()
        val free = (capacity - active).coerceAtLeast(0)
        val admit = minOf(rateBatch.toLong(), free).toInt()
        if (admit <= 0) return 0
        val users = queueRepository.popNext(admit)
        users.forEach { userId ->
            queueRepository.issueToken(userId, UUID.randomUUID().toString(), ttlSeconds, nowMillis)
        }
        return users.size
    }
}
```

`infrastructure/queue/QueuePromoteScheduler.kt` (R7 OutboxRelayScheduler 패턴):
```kotlin
package com.loopers.infrastructure.queue

import com.loopers.application.queue.usecase.PromoteQueueUsecase
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

@ConditionalOnProperty(name = ["queue.promoter.scheduler.enabled"], havingValue = "true", matchIfMissing = true)
@Component
class QueuePromoteScheduler(
    private val promoteQueueUsecase: PromoteQueueUsecase,
) {
    private val log = LoggerFactory.getLogger(QueuePromoteScheduler::class.java)

    @Scheduled(fixedDelay = 100)
    fun promote() {
        runCatching { promoteQueueUsecase.promoteOnce(Instant.now().toEpochMilli()) }
            .onFailure { log.warn("Queue promote tick failed", it) }
    }
}
```

`application.yml` — `on-profile: local, test` 블록에 추가:
```yaml
queue:
  promoter:
    scheduler:
      enabled: false
```

- [ ] **Step 4: 통과 확인** — test PASS.

- [ ] **Step 5: 커밋**
```bash
./gradlew :apps:commerce-api:ktlintCheck -q
git add apps/commerce-api/src/main/kotlin/com/loopers/application/queue/usecase/PromoteQueueUsecase.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/queue/QueuePromoteScheduler.kt \
        apps/commerce-api/src/main/resources/application.yml \
        apps/commerce-api/src/test/kotlin/com/loopers/application/queue/PromoteQueueUsecaseIntegrationTest.kt
git commit -m "feat: leaky-bucket promote usecase + scheduler (capacity+rate) (R8-2)"
```

---

### Task 5: 주문 게이트 (EntryTokenGate + 헤더 검증, fail-open) + 신규 ErrorType

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/support/error/ErrorType.kt` (`TOO_MANY_REQUESTS` 추가)
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/queue/EntryTokenGate.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/order/OrderV1Controller.kt` (헤더 + 게이트)
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/application/queue/EntryTokenGateIntegrationTest.kt`

**Interfaces:**
- Consumes: `OrderQueueRepository`(findToken/consume), `UserService`.
- Produces: `EntryTokenGate.validate(loginId, password, token: String?): Long`(userId 반환; 유효 토큰 아니면 `CoreException(TOO_MANY_REQUESTS)`, Redis 예외 시 bypass), `EntryTokenGate.consume(userId: Long)`.

- [ ] **Step 1: 실패 테스트 작성** — `EntryTokenGateIntegrationTest.kt`:
```kotlin
package com.loopers.application.queue

import com.loopers.domain.queue.OrderQueueRepository
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class EntryTokenGateIntegrationTest {
    @Autowired lateinit var gate: EntryTokenGate
    @Autowired lateinit var repository: OrderQueueRepository
    @Autowired lateinit var userService: UserService
    @Autowired lateinit var redisCleanUp: RedisCleanUp

    @AfterEach fun tearDown() = redisCleanUp.truncateAll()

    @DisplayName("발급된 토큰과 일치하면 통과(userId 반환)하고, consume하면 토큰이 사라진다.")
    @Test
    fun validTokenPasses() {
        // arrange: 유저 u 생성, 토큰 발급
        repository.issueToken(u.id, "tok-1", 300, 1000)
        val userId = gate.validate(u.loginId, u.rawPassword, "tok-1")
        assertThat(userId).isEqualTo(u.id)
        gate.consume(u.id)
        assertThat(repository.findToken(u.id)).isNull()
    }

    @DisplayName("토큰이 없거나 불일치면 TOO_MANY_REQUESTS로 차단한다.")
    @Test
    fun invalidTokenBlocked() {
        assertThatThrownBy { gate.validate(u.loginId, u.rawPassword, "wrong") }
            .isInstanceOf(CoreException::class.java)
            .extracting("errorType").isEqualTo(ErrorType.TOO_MANY_REQUESTS)
    }
}
```
> 유저 생성은 기존 통합테스트 방식. Redis-down bypass 케이스는 T6 degradation 테스트에서 다룬다(여기선 정상 Redis 경로).

- [ ] **Step 2: 실패 확인** — FAIL(미해결/미차단).

- [ ] **Step 3: 구현**

`ErrorType.kt` — 마지막에 추가:
```kotlin
    TOO_MANY_REQUESTS(
        HttpStatus.TOO_MANY_REQUESTS,
        HttpStatus.TOO_MANY_REQUESTS.reasonPhrase,
        "대기열 입장 토큰이 유효하지 않습니다. 순번을 기다려주세요.",
    ),
```

`application/queue/EntryTokenGate.kt` (fail-open: Redis 예외 → bypass; 정상인데 불일치 → 차단):
```kotlin
package com.loopers.application.queue

import com.loopers.domain.queue.OrderQueueRepository
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class EntryTokenGate(
    private val userService: UserService,
    private val queueRepository: OrderQueueRepository,
) {
    private val log = LoggerFactory.getLogger(EntryTokenGate::class.java)

    fun validate(loginId: String, password: String, token: String?): Long {
        val user = userService.getProfile(loginId = loginId, password = password)
        val stored = try {
            queueRepository.findToken(user.id)
        } catch (e: Exception) {
            // fail-open: Redis 장애 시 게이트 우회(서비스 유지 우선). 경보.
            log.warn("Redis unavailable — bypassing entry-token gate (fail-open). userId={}", user.id, e)
            return user.id
        }
        if (token.isNullOrBlank() || stored == null || stored != token) {
            throw CoreException(ErrorType.TOO_MANY_REQUESTS)
        }
        return user.id
    }

    fun consume(userId: Long) {
        runCatching { queueRepository.consume(userId) }
            .onFailure { log.warn("Failed to consume entry token. userId={}", userId, it) }
    }
}
```

`OrderV1Controller.kt` — 헤더 추가 + 게이트 감싸기:
```kotlin
@RestController
@RequestMapping("/api/v1/orders")
class OrderV1Controller(
    private val createOrderUsecase: CreateOrderUsecase,
    private val entryTokenGate: EntryTokenGate,
) {
    @PostMapping
    fun order(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") password: String,
        @RequestHeader(value = "X-Entry-Token", required = false) entryToken: String?,
        @RequestBody request: OrderV1Dto.OrderRequest,
    ): ApiResponse<OrderV1Dto.OrderResponse> {
        val userId = entryTokenGate.validate(loginId, password, entryToken)
        val response = createOrderUsecase.execute(request.toCommand(loginId = loginId, password = password))
            .let { OrderV1Dto.OrderResponse.from(it) }
        entryTokenGate.consume(userId)   // 주문 완료 후 토큰 삭제
        return ApiResponse.success(response)
    }
}
```
> import: `com.loopers.application.queue.EntryTokenGate`. `CoreException`가 `errorType` 프로퍼티를 갖는지 확인(테스트 `extracting("errorType")` 기준) — 다르면 실제 필드명으로 조정.

- [ ] **Step 4: 통과 확인** — test PASS. 기존 주문 E2E 테스트가 있으면 `X-Entry-Token` 없이 호출 시 차단되므로, 해당 테스트에 토큰 발급/헤더를 추가하거나 게이트를 우회하도록 조정(주문 E2E는 게이트 통과 경로로 갱신). 전체 `:apps:commerce-api:test`로 회귀 확인.

- [ ] **Step 5: 커밋**
```bash
./gradlew :apps:commerce-api:ktlintCheck -q
git add apps/commerce-api/src/main/kotlin/com/loopers/support/error/ErrorType.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/application/queue/EntryTokenGate.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/order/OrderV1Controller.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/application/queue/EntryTokenGateIntegrationTest.kt
git commit -m "feat: order entry-token gate (fail-open) + TOO_MANY_REQUESTS (R8-2)"
```

---

### Task 6: 예상 대기시간 + position 응답 확장(토큰/예상대기) + fail-open degradation

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/queue/QueuePosition.kt` (필드 확장)
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/queue/usecase/GetQueuePositionUsecase.kt` (예상대기·토큰·degradation)
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/queue/QueueV1Dto.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/application/queue/QueuePositionEnrichTest.kt`

**Interfaces:**
- `QueuePosition(position: Long?, waiting: Boolean, estimatedWaitSeconds: Long?, token: String?)`.
- position 응답: 대기 중이면 `position`+`estimatedWaitSeconds`(=rank/rate), 발급됐으면(토큰 있음) `position=0`+`token`, 미진입/만료면 `waiting=false`. Redis 장애 시 degraded(모두 null).

- [ ] **Step 1: 실패 테스트 작성** — `QueuePositionEnrichTest.kt`:
```kotlin
package com.loopers.application.queue

import com.loopers.application.queue.usecase.GetQueuePositionUsecase
import com.loopers.domain.queue.OrderQueueRepository
import com.loopers.domain.user.UserService
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

@TestPropertySource(properties = ["queue.throughput-per-second=50"])
@SpringBootTest
class QueuePositionEnrichTest {
    @Autowired lateinit var getPosition: GetQueuePositionUsecase
    @Autowired lateinit var repository: OrderQueueRepository
    @Autowired lateinit var userService: UserService
    @Autowired lateinit var redisCleanUp: RedisCleanUp

    @AfterEach fun tearDown() = redisCleanUp.truncateAll()

    @DisplayName("대기 중이면 예상 대기시간(rank/throughput)이 계산된다.")
    @Test
    fun estimatesWait() {
        // arrange: 유저 100명 진입 후 대상 유저 u(rank=99)
        // ...
        val result = getPosition.execute(u.loginId, u.rawPassword)
        assertThat(result.waiting).isTrue()
        assertThat(result.estimatedWaitSeconds).isEqualTo(99 / 50)   // ≈1s (정수)
    }

    @DisplayName("토큰이 발급되면 position=0, token 동봉.")
    @Test
    fun tokenIssued() {
        repository.issueToken(u.id, "tok-1", 300, 1000)   // waiting에는 없음
        val result = getPosition.execute(u.loginId, u.rawPassword)
        assertThat(result.position).isEqualTo(0L)
        assertThat(result.token).isEqualTo("tok-1")
    }
}
```

- [ ] **Step 2: 실패 확인** — FAIL.

- [ ] **Step 3: 구현**

`QueuePosition.kt`:
```kotlin
package com.loopers.application.queue

data class QueuePosition(
    val position: Long?,
    val waiting: Boolean,
    val estimatedWaitSeconds: Long?,
    val token: String?,
)
```

`GetQueuePositionUsecase.kt` (rank→예상대기 / 토큰→position0 / 미진입 / Redis장애→degraded):
```kotlin
package com.loopers.application.queue.usecase

import com.loopers.application.queue.QueuePosition
import com.loopers.domain.queue.OrderQueueRepository
import com.loopers.domain.user.UserService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class GetQueuePositionUsecase(
    private val userService: UserService,
    private val queueRepository: OrderQueueRepository,
    @Value("\${queue.throughput-per-second:175}") private val throughputPerSecond: Long,
) {
    private val log = LoggerFactory.getLogger(GetQueuePositionUsecase::class.java)

    fun execute(loginId: String, password: String): QueuePosition {
        val user = userService.getProfile(loginId = loginId, password = password)
        return try {
            val rank = queueRepository.rank(user.id)
            if (rank != null) {
                QueuePosition(
                    position = rank + 1,
                    waiting = true,
                    estimatedWaitSeconds = rank / throughputPerSecond,
                    token = null,
                )
            } else {
                val token = queueRepository.findToken(user.id)
                if (token != null) {
                    QueuePosition(position = 0, waiting = false, estimatedWaitSeconds = 0, token = token)
                } else {
                    QueuePosition(position = null, waiting = false, estimatedWaitSeconds = null, token = null)
                }
            }
        } catch (e: Exception) {
            // fail-open degradation: Redis 장애 시 순번 조회 불가 → degraded 응답(주문은 게이트 bypass로 가능).
            log.warn("Redis unavailable — degraded queue position. userId={}", user.id, e)
            QueuePosition(position = null, waiting = false, estimatedWaitSeconds = null, token = null)
        }
    }
}
```

`QueueV1Dto.PositionResponse` — 필드 확장:
```kotlin
    data class PositionResponse(
        val position: Long?,
        val waiting: Boolean,
        val estimatedWaitSeconds: Long?,
        val token: String?,
    ) {
        companion object {
            fun from(p: QueuePosition) = PositionResponse(p.position, p.waiting, p.estimatedWaitSeconds, p.token)
        }
    }
```

- [ ] **Step 4: 통과 확인** — test PASS. 전체 `:apps:commerce-api:test` 회귀.

- [ ] **Step 5: 커밋 + .http**
```bash
./gradlew :apps:commerce-api:test -q
./gradlew :apps:commerce-api:ktlintCheck -q
# http/commerce-api/queue-v1.http 에 enter/position/order(with X-Entry-Token) 예시 추가
git add apps/commerce-api/src/main/kotlin/com/loopers/application/queue/ \
        apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/queue/QueueV1Dto.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/application/queue/QueuePositionEnrichTest.kt \
        http/commerce-api/queue-v1.http
git commit -m "feat: estimated wait + position enrichment + fail-open degradation (R8-3)"
```

---

## 완료 기준 (DoD)

- ZSet 대기열(enter/position/total, 중복방지) + 입장 토큰(TTL) + leaky bucket 스케줄러(capacity+rate) + 주문 게이트(fail-open) + 예상대기 폴링.
- 검증: 동시 진입 순서 정확 / 토큰 TTL 만료 / 처리량 초과 시 배치만큼만 발급·나머지 대기 / capacity 소진→소비 후 재발급 / Redis 장애 시 게이트 bypass.
- `./gradlew :apps:commerce-api:test`, `:apps:commerce-api:ktlintCheck` 통과. (streamer/batch 무관)

## Self-Review (spec 대비)

- **Step 1 ZSet 대기열/순번/중복/전체(§Checklist)** → Task 1,2. ✅
- **Step 2 토큰 발급·TTL·검증·삭제·배치 산정(§Checklist)** → Task 3,4,5. capacity/rateBatch/ttl 프로퍼티 + 산정 근거는 spec §8. ✅
- **Step 3 예상대기·폴링·토큰 동봉(§Checklist)** → Task 6. ✅
- **Leaky bucket(D4)** → Task 4 promoteOnce(prune→active→min(rateBatch,C-active)). ✅
- **Fail-open(D7)** → Task 5 gate + Task 6 position degradation. ✅
- **주문 게이트(D5), CreateOrderUsecase 불변** → Task 5(헤더+게이트, usecase 미변경). ✅
- 검증 3종(동시진입/TTL만료/처리량초과) → Task 1(동시), 3(TTL/prune via 만료), 4(처리량초과). ✅
- 타입 일관성: `OrderQueueRepository` 프리미티브(T1/T3) = 소비(T2/T4/T5/T6). `QueuePosition`(T2 정의→T6 확장) 필드 일관. `promoteOnce(nowMillis):Int`(T4)=스케줄러 호출.

## 리스크 / 주의

- 큐 repo 프리미티브는 **예외 전파**(캐시 repo와 다름) — fail-open은 gate/usecase가 소유.
- `issueToken`(SET + ZADD) 비원자 — 단일 스케줄러 스레드 + 프룬/TTL 자가치유로 감수. 원자성 필요 시 Lua script로 업그레이드(범위 밖).
- 기존 주문 E2E 테스트는 게이트 추가로 `X-Entry-Token` 필요 → 토큰 발급 후 헤더 포함하도록 갱신(Task 5 Step 4).
- `CoreException`의 필드명(`errorType` 등) 실제 정의 확인해 테스트 assert 조정.
- 다중 인스턴스 스케줄러/Lua 원자화/SSE·동적폴링·Jitter는 범위 밖(문서화).
- Docker 필요(전 태스크 Redis 컨테이너; 게이트/주문 회귀는 MySQL+Kafka도).
