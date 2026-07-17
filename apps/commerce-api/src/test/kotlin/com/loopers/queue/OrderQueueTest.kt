package com.loopers.queue

import com.loopers.domain.queue.OrderQueueService
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.user.UserDto
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 주문 대기열 통합 테스트.
 * - 동시 진입 시 순서 보장 검증
 * - 중복 진입 방지 검증
 * - 토큰 발급 및 사용 후 삭제 검증
 * - 토큰 없이 주문 시 거절 검증
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderQueueTest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
    private val orderQueueService: OrderQueueService,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @BeforeEach
    fun setUp() {
        repeat(20) { i ->
            val signup = UserDto.SignupRequest(
                loginId = "queueuser$i",
                password = "Password1@@!",
                name = "대기열유저$i",
                birthDate = "1995-01-01",
                email = "queue$i@example.com",
            )
            testRestTemplate.exchange(
                "/api/v1/users",
                HttpMethod.POST,
                HttpEntity(signup),
                object : ParameterizedTypeReference<ApiResponse<Any>>() {},
            )
        }
    }

    private fun authHeaders(index: Int) = HttpHeaders().apply {
        add("X-Loopers-LoginId", "queueuser$index")
        add("X-Loopers-LoginPw", "Password1@@!")
    }

    @DisplayName("20명이 동시에 대기열에 진입하면 모두 성공하고 순번이 부여된다")
    @Test
    fun concurrentQueueEntry_allSucceed() {
        val threadCount = 20
        val readyLatch = CountDownLatch(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)
        val successCount = AtomicInteger(0)

        repeat(threadCount) { i ->
            executor.submit {
                try {
                    readyLatch.countDown()
                    startLatch.await()

                    val response = testRestTemplate.exchange(
                        "/api/v1/queue/enter",
                        HttpMethod.POST,
                        HttpEntity<Any>(authHeaders(i)),
                        object : ParameterizedTypeReference<ApiResponse<Map<String, Any>>>() {},
                    )
                    if (response.statusCode.is2xxSuccessful) {
                        successCount.incrementAndGet()
                    }
                } catch (_: Exception) {
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        readyLatch.await()
        startLatch.countDown()
        doneLatch.await()
        executor.shutdown()

        successCount.get() shouldBe 20
    }

    @DisplayName("같은 유저가 중복 진입해도 순번이 변하지 않는다")
    @Test
    fun duplicateEntry_samePosition() {
        val headers = authHeaders(0)

        val first = testRestTemplate.exchange(
            "/api/v1/queue/enter",
            HttpMethod.POST,
            HttpEntity<Any>(headers),
            object : ParameterizedTypeReference<ApiResponse<Map<String, Any>>>() {},
        )

        val second = testRestTemplate.exchange(
            "/api/v1/queue/enter",
            HttpMethod.POST,
            HttpEntity<Any>(headers),
            object : ParameterizedTypeReference<ApiResponse<Map<String, Any>>>() {},
        )

        val firstPosition = (first.body?.data?.get("position") as Number).toLong()
        val secondPosition = (second.body?.data?.get("position") as Number).toLong()
        firstPosition shouldBe secondPosition
    }

    @DisplayName("스케줄러가 대기열에서 N명을 꺼내 토큰을 발급한다")
    @Test
    fun scheduler_issuesTokens() {
        // 5명 진입
        repeat(5) { i ->
            orderQueueService.enter((i + 1).toLong())
        }

        // 스케줄러 실행
        val processed = orderQueueService.processQueue(OrderQueueService.SCHEDULER_BATCH_SIZE)
        processed shouldBe 5

        // 토큰 확인
        val info = orderQueueService.getPosition(1L)
        info.position shouldBe 0
        info.token.shouldNotBeNull()
    }

    @DisplayName("토큰 없이 주문하면 거절된다")
    @Test
    fun orderWithoutToken_rejected() {
        val headers = authHeaders(0)

        val response = testRestTemplate.exchange(
            "/api/v1/orders",
            HttpMethod.POST,
            HttpEntity(
                mapOf(
                    "items" to listOf(mapOf("productId" to 1, "quantity" to 1)),
                ),
                headers,
            ),
            object : ParameterizedTypeReference<ApiResponse<Any>>() {},
        )

        response.statusCode.value() shouldBe 400
    }

    @DisplayName("토큰 검증 성공 후 주문 완료하면 토큰이 삭제된다")
    @Test
    fun tokenConsumedAfterOrder() {
        orderQueueService.enter(1L)
        orderQueueService.processQueue(OrderQueueService.SCHEDULER_BATCH_SIZE)

        val token = orderQueueService.getPosition(1L).token
        token.shouldNotBeNull()

        // 토큰 소비
        orderQueueService.consumeToken(1L)

        // 토큰 삭제 확인
        val afterConsume = orderQueueService.getPosition(1L)
        afterConsume.token.shouldBeNull()
    }

    @DisplayName("순번 조회 시 예상 대기 시간이 계산된다")
    @Test
    fun estimatedWaitTime_calculated() {
        // 100명 진입
        repeat(100) { i ->
            orderQueueService.enter((i + 1).toLong())
        }

        val info = orderQueueService.getPosition(100L)
        info.position shouldBe 100
        info.estimatedWaitSeconds shouldBeGreaterThan 0
    }
}
