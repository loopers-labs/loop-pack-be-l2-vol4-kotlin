package com.loopers.application.coupon

import com.loopers.application.coupon.usecase.IssueCouponFromRequestUsecase
import com.loopers.domain.coupon.CouponIssueRequest
import com.loopers.domain.coupon.CouponIssueRequestRepository
import com.loopers.domain.coupon.CouponIssueStatus
import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.support.runConcurrently
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.Pageable
import java.math.BigDecimal
import java.time.ZonedDateTime

@SpringBootTest
class IssueCouponFromRequestConcurrencyTest {
    @Autowired lateinit var issueUsecase: IssueCouponFromRequestUsecase

    @Autowired lateinit var couponRepository: CouponRepository

    @Autowired lateinit var userCouponRepository: UserCouponRepository

    @Autowired lateinit var requestRepository: CouponIssueRequestRepository

    @Autowired lateinit var databaseCleanUp: DatabaseCleanUp

    @AfterEach fun tearDown() = databaseCleanUp.truncateAllTables()

    @DisplayName("총 N개 쿠폰에 M(>N)명이 동시에 발급 처리돼도 정확히 N명만 ISSUED, 나머지는 REJECTED(SOLD_OUT), 초과발급 0.")
    @Test
    fun issuesExactlyTotalQuantityUnderConcurrency() {
        // arrange
        val total = 100
        val users = 300
        val coupon = couponRepository.save(
            CouponModel(
                name = "선착순",
                type = CouponType.FIXED,
                discountValue = BigDecimal("1000"),
                minOrderAmount = null,
                expiredAt = ZonedDateTime.now().plusDays(1),
                totalQuantity = total,
            ),
        )
        val requestIds = (0 until users).map { "req-$it" }
        requestIds.forEachIndexed { i, rid ->
            requestRepository.save(CouponIssueRequest(requestId = rid, userId = (i + 1).toLong(), couponId = coupon.id))
        }

        // act
        runConcurrently(threadCount = users) { i ->
            issueUsecase.issue(requestId = requestIds[i], userId = (i + 1).toLong(), couponId = coupon.id)
        }

        // assert
        val issued = requestIds.count { requestRepository.findByRequestId(it)?.status == CouponIssueStatus.ISSUED }
        assertThat(issued).isEqualTo(total)
        assertThat(userCouponRepository.findAllByCouponId(coupon.id, Pageable.unpaged()).content).hasSize(total)
        assertThat(couponRepository.findActiveById(coupon.id)?.issuedCount).isEqualTo(total)
    }
}
