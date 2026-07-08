package com.loopers.application.coupon

import com.loopers.domain.coupon.CouponIssueMessage
import com.loopers.domain.coupon.CouponIssueRequestService
import com.loopers.domain.coupon.CouponIssueStatus
import com.loopers.domain.coupon.CouponService
import com.loopers.domain.coupon.DiscountType
import com.loopers.infrastructure.coupon.CouponIssueRequestJpaRepository
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.ZonedDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
class CouponIssueProcessorTest @Autowired constructor(
    private val couponService: CouponService,
    private val couponIssueRequestService: CouponIssueRequestService,
    private val couponIssueProcessor: CouponIssueProcessor,
    private val couponIssueRequestJpaRepository: CouponIssueRequestJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    private val notExpired = ZonedDateTime.now().plusDays(1)

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @DisplayName("선착순 100장에 300명이 동시에 발급 요청해도, 정확히 100장만 발급되고 초과 발급이 없다.")
    @Test
    fun noOversell_underConcurrency() {
        // arrange
        val limit = 100L
        val requesters = 300
        val coupon = couponService.register("선착순", DiscountType.FIXED, 1_000, null, notExpired, issuableQuantity = limit)
        repeat(requesters) { i ->
            couponIssueRequestService.create("req-$i", (i + 1).toLong(), coupon.id)
        }
        val latch = CountDownLatch(requesters)
        val executor = Executors.newFixedThreadPool(32)

        // act: 300명이 동시에 발급 처리
        repeat(requesters) { i ->
            executor.submit {
                try {
                    couponIssueProcessor.process(CouponIssueMessage("req-$i", (i + 1).toLong(), coupon.id))
                } finally {
                    latch.countDown()
                }
            }
        }
        latch.await(30, TimeUnit.SECONDS)
        executor.shutdown()

        // assert
        val requests = couponIssueRequestJpaRepository.findAll()
        val success = requests.count { it.status == CouponIssueStatus.SUCCESS }
        val failed = requests.count { it.status == CouponIssueStatus.FAILED }
        val reloaded = couponService.getById(coupon.id)
        assertAll(
            { assertThat(success).isEqualTo(100) },
            { assertThat(failed).isEqualTo(200) },
            { assertThat(reloaded.issuedQuantity).isEqualTo(100L) },
            { assertThat(requests.all { it.reason == null || it.reason == "SOLD_OUT" }).isTrue() },
        )
    }

    @DisplayName("같은 발급 요청을 두 번 처리해도, 발급은 한 번만 반영된다 (멱등).")
    @Test
    fun idempotent_whenSameRequestProcessedTwice() {
        // arrange
        val coupon = couponService.register("쿠폰", DiscountType.FIXED, 1_000, null, notExpired, issuableQuantity = 100)
        couponIssueRequestService.create("req-1", 1L, coupon.id)

        // act
        couponIssueProcessor.process(CouponIssueMessage("req-1", 1L, coupon.id))
        couponIssueProcessor.process(CouponIssueMessage("req-1", 1L, coupon.id))

        // assert
        assertThat(couponService.getById(coupon.id).issuedQuantity).isEqualTo(1L)
    }

    @DisplayName("한 사용자가 서로 다른 요청으로 두 번 발급받아도, 쿠폰은 1장만 나가고 수량도 1만 소모된다.")
    @Test
    fun duplicateUser_consumesQuantityOnce() {
        // arrange
        val coupon = couponService.register("쿠폰", DiscountType.FIXED, 1_000, null, notExpired, issuableQuantity = 100)
        couponIssueRequestService.create("req-a", 1L, coupon.id)
        couponIssueRequestService.create("req-b", 1L, coupon.id)

        // act
        couponIssueProcessor.process(CouponIssueMessage("req-a", 1L, coupon.id))
        couponIssueProcessor.process(CouponIssueMessage("req-b", 1L, coupon.id))

        // assert
        assertThat(couponService.getById(coupon.id).issuedQuantity).isEqualTo(1L)
    }

    @DisplayName("수량이 소진되면, 이후 요청은 SOLD_OUT 으로 실패 처리된다.")
    @Test
    fun marksFailed_whenSoldOut() {
        // arrange
        val coupon = couponService.register("한정", DiscountType.FIXED, 1_000, null, notExpired, issuableQuantity = 1)
        couponIssueRequestService.create("req-1", 1L, coupon.id)
        couponIssueRequestService.create("req-2", 2L, coupon.id)

        // act
        couponIssueProcessor.process(CouponIssueMessage("req-1", 1L, coupon.id))
        couponIssueProcessor.process(CouponIssueMessage("req-2", 2L, coupon.id))

        // assert
        val second = couponIssueRequestJpaRepository.findByRequestId("req-2")!!
        assertAll(
            { assertThat(second.status).isEqualTo(CouponIssueStatus.FAILED) },
            { assertThat(second.reason).isEqualTo("SOLD_OUT") },
            { assertThat(couponService.getById(coupon.id).issuedQuantity).isEqualTo(1L) },
        )
    }
}
