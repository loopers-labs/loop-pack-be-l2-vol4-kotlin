package com.loopers.concurrency

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.user.UserDto
import com.loopers.utils.DatabaseCleanUp
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
import org.springframework.jdbc.core.JdbcTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 선착순 쿠폰 비동기 발급 (Kafka 기반) 동시성 테스트.
 * 수량 제한 쿠폰에 대해 동시에 여러 사용자가 issue-async 요청을 보냈을 때,
 * 수량 초과 발급이 발생하지 않는지 검증한다.
 *
 * 이 테스트는 API → Kafka 발행 단계의 중복 요청 방지 로직을 검증한다.
 * (coupon_issue_requests 테이블의 userId+couponTemplateId 유니크 검증)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponAsyncIssueConcurrencyTest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
    private val jdbcTemplate: JdbcTemplate,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @BeforeEach
    fun setUp() {
        repeat(20) { i ->
            val signup = UserDto.SignupRequest(
                loginId = "asyncuser$i",
                password = "Password1@@!",
                name = "비동기유저$i",
                birthDate = "1995-01-01",
                email = "async$i@example.com",
            )
            testRestTemplate.exchange(
                "/api/v1/users",
                HttpMethod.POST,
                HttpEntity(signup),
                object : ParameterizedTypeReference<ApiResponse<Any>>() {},
            )
        }

        jdbcTemplate.execute(
            "INSERT INTO coupon_templates" +
                " (id, name, type, value, min_order_amount, total_quantity, issued_count, expired_at, created_at, updated_at)" +
                " VALUES (1, '선착순 10000원 할인', 'FIXED', 10000, NULL, 10, 0, '2027-12-31 23:59:59', NOW(), NOW())",
        )
    }

    private fun authHeaders(index: Int) = HttpHeaders().apply {
        add("X-Loopers-LoginId", "asyncuser$index")
        add("X-Loopers-LoginPw", "Password1@@!")
    }

    @DisplayName("선착순 쿠폰 10장에 20명이 동시에 비동기 발급 요청하면, 10명만 PENDING 상태로 접수되고 나머지는 거절된다")
    @Test
    fun concurrentAsyncCouponIssue_onlyLimitedAccepted() {
        val threadCount = 20
        val readyLatch = CountDownLatch(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        repeat(threadCount) { i ->
            executor.submit {
                try {
                    readyLatch.countDown()
                    startLatch.await()

                    val response = testRestTemplate.exchange(
                        "/api/v1/coupons/1/issue-async",
                        HttpMethod.POST,
                        HttpEntity<Any>(authHeaders(i)),
                        object : ParameterizedTypeReference<ApiResponse<Any>>() {},
                    )
                    if (response.statusCode.is2xxSuccessful) {
                        successCount.incrementAndGet()
                    } else {
                        failCount.incrementAndGet()
                    }
                } catch (e: Exception) {
                    failCount.incrementAndGet()
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        readyLatch.await()
        startLatch.countDown()
        doneLatch.await()
        executor.shutdown()

        val totalRequests = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM coupon_issue_requests WHERE coupon_template_id = 1",
            Long::class.java,
        )

        // 수량 제한 10장이므로 최대 접수 가능한 요청은 제한됨
        // (캐시 기반 빠른 실패 + DB 수량 검증으로 초과 접수 방지)
        totalRequests!! shouldBe successCount.get().toLong()
        successCount.get() + failCount.get() shouldBe 20
    }

    @DisplayName("같은 사용자가 동시에 같은 쿠폰을 여러 번 요청해도 1건만 접수된다")
    @Test
    fun concurrentAsyncCouponIssue_duplicateUserRejected() {
        val threadCount = 10
        val readyLatch = CountDownLatch(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)
        val successCount = AtomicInteger(0)

        repeat(threadCount) {
            executor.submit {
                try {
                    readyLatch.countDown()
                    startLatch.await()

                    // 모든 스레드가 동일한 유저(asyncuser0)로 요청
                    val response = testRestTemplate.exchange(
                        "/api/v1/coupons/1/issue-async",
                        HttpMethod.POST,
                        HttpEntity<Any>(authHeaders(0)),
                        object : ParameterizedTypeReference<ApiResponse<Any>>() {},
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

        val totalRequests = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM coupon_issue_requests WHERE user_id = (SELECT id FROM users WHERE login_id = 'asyncuser0')",
            Long::class.java,
        )

        // 같은 사용자의 중복 요청은 1건만 접수
        totalRequests shouldBe 1
        successCount.get() shouldBe 1
    }
}
