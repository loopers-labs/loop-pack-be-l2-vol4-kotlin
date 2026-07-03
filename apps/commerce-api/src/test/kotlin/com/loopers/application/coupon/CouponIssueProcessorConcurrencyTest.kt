package com.loopers.application.coupon

import com.loopers.application.coupon.CouponIssueProcessor.Companion.couponIssuedCountKey
import com.loopers.config.redis.RedisConfig
import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponIssueStatus
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.DiscountPolicy
import com.loopers.infrastructure.coupon.CouponIssueResultJpaEntity
import com.loopers.infrastructure.coupon.CouponIssueResultJpaRepository
import com.loopers.infrastructure.coupon.UserCouponJpaRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

@SpringBootTest
class CouponIssueProcessorConcurrencyTest @Autowired constructor(
    private val couponIssueProcessor: CouponIssueProcessor,
    private val couponRepository: CouponRepository,
    private val couponIssueResultJpaRepository: CouponIssueResultJpaRepository,
    private val userCouponJpaRepository: UserCouponJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val kafkaListenerEndpointRegistry: KafkaListenerEndpointRegistry,
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
) {
    @BeforeEach
    fun setUp() {
        kafkaListenerEndpointRegistry.allListenerContainers.forEach { it.stop() }
        databaseCleanUp.truncateAllTables()
        redisTemplate.keys("coupon:*:issued")?.forEach { redisTemplate.delete(it) }
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisTemplate.keys("coupon:*:issued")?.forEach { redisTemplate.delete(it) }
        kafkaListenerEndpointRegistry.allListenerContainers.forEach { it.start() }
    }

    @DisplayName("선착순 쿠폰 동시성 제어 시, ")
    @Nested
    inner class ConcurrentIssue {

        @DisplayName("선착순 수량보다 많은 요청이 동시에 들어와도 정확히 수량만큼만 발급된다.")
        @Test
        fun doesNotExceedMaxIssueCount_whenConcurrentRequests() {
            // arrange
            val maxIssueCount = 10
            val totalRequests = 30
            val coupon = couponRepository.save(
                Coupon(
                    name = "선착순 쿠폰",
                    policy = DiscountPolicy.FixedAmount(1_000L),
                    maxIssueCount = maxIssueCount,
                ),
            )

            val requests = (1..totalRequests).map { index ->
                val requestId = UUID.randomUUID().toString()
                couponIssueResultJpaRepository.save(
                    CouponIssueResultJpaEntity(
                        requestId = requestId,
                        userId = index.toLong(),
                        couponId = coupon.id!!,
                        status = CouponIssueStatus.PENDING,
                    ),
                )
                IssueRequest(
                    eventId = UUID.randomUUID().toString(),
                    requestId = requestId,
                    userId = index.toLong(),
                    couponId = coupon.id!!,
                )
            }

            // act
            val results = runConcurrently(totalRequests) { index ->
                couponIssueProcessor.process(
                    eventId = requests[index].eventId,
                    eventType = "CouponIssueRequestedEvent",
                    requestId = requests[index].requestId,
                    userId = requests[index].userId,
                    couponId = requests[index].couponId,
                )
            }

            // assert
            val couponId = coupon.id!!
            val issuedCoupons = userCouponJpaRepository.findAll()
                .filter { it.couponId == couponId }
            val allResults = couponIssueResultJpaRepository.findAll()
                .filter { it.couponId == couponId }
            val successResults = allResults.filter { it.status == CouponIssueStatus.SUCCESS }
            val failedResults = allResults.filter { it.status == CouponIssueStatus.FAILED }
            val redisCount = redisTemplate.opsForValue().get(couponIssuedCountKey(couponId))?.toLong() ?: 0

            assertAll(
                { assertThat(results).allSatisfy { assertThat(it.isSuccess).isTrue() } },
                { assertThat(issuedCoupons).hasSize(maxIssueCount) },
                { assertThat(successResults).hasSize(maxIssueCount) },
                { assertThat(failedResults).hasSize(totalRequests - maxIssueCount) },
                { assertThat(redisCount).isEqualTo(maxIssueCount.toLong()) },
            )
        }

        @DisplayName("동일 유저가 같은 쿠폰에 대해 동시에 발급 요청해도 1건만 발급된다.")
        @Test
        fun issuesOnlyOnce_whenSameUserRequestsConcurrently() {
            // arrange
            val userId = 1L
            val coupon = couponRepository.save(
                Coupon(
                    name = "중복 테스트 쿠폰",
                    policy = DiscountPolicy.FixedAmount(1_000L),
                    maxIssueCount = 100,
                ),
            )

            val requests = (1..5).map {
                val requestId = UUID.randomUUID().toString()
                couponIssueResultJpaRepository.save(
                    CouponIssueResultJpaEntity(
                        requestId = requestId,
                        userId = userId,
                        couponId = coupon.id!!,
                        status = CouponIssueStatus.PENDING,
                    ),
                )
                IssueRequest(
                    eventId = UUID.randomUUID().toString(),
                    requestId = requestId,
                    userId = userId,
                    couponId = coupon.id!!,
                )
            }

            // act
            runConcurrently(requests.size) { index ->
                couponIssueProcessor.process(
                    eventId = requests[index].eventId,
                    eventType = "CouponIssueRequestedEvent",
                    requestId = requests[index].requestId,
                    userId = requests[index].userId,
                    couponId = requests[index].couponId,
                )
            }

            // assert
            val issuedCoupons = userCouponJpaRepository.findAll()
                .filter { it.userId == userId && it.couponId == coupon.id!! }
            val successResults = couponIssueResultJpaRepository.findAll()
                .filter { it.status == CouponIssueStatus.SUCCESS }

            assertAll(
                { assertThat(issuedCoupons).hasSize(1) },
                { assertThat(successResults).hasSize(1) },
            )
        }
    }

    private data class IssueRequest(
        val eventId: String,
        val requestId: String,
        val userId: Long,
        val couponId: Long,
    )

    private fun <T> runConcurrently(
        times: Int,
        task: (Int) -> T,
    ): List<Result<T>> {
        val executor = Executors.newFixedThreadPool(times)
        val ready = CountDownLatch(times)
        val start = CountDownLatch(1)

        return try {
            val futures = (0 until times).map { index ->
                executor.submit(
                    Callable {
                        ready.countDown()
                        start.await()
                        runCatching { task(index) }
                    },
                )
            }
            ready.await()
            start.countDown()
            futures.map { it.get() }
        } finally {
            executor.shutdownNow()
        }
    }
}
