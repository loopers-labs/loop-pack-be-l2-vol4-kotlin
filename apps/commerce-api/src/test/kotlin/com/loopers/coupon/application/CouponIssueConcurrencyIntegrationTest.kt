package com.loopers.coupon.application

import com.loopers.coupon.domain.Coupon
import com.loopers.coupon.domain.CouponErrorCode
import com.loopers.coupon.domain.CouponRepository
import com.loopers.coupon.domain.CouponType
import com.loopers.coupon.domain.UserCouponGrantedType
import com.loopers.coupon.infrastructure.UserCouponJpaRepository
import com.loopers.shared.domain.Money
import com.loopers.support.DatabaseCleanup
import com.loopers.support.error.ConflictException
import com.loopers.support.runConcurrently
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class CouponIssueConcurrencyIntegrationTest @Autowired constructor(
    private val couponService: CouponService,
    private val couponRepository: CouponRepository,
    private val userCouponJpaRepository: UserCouponJpaRepository,
    private val databaseCleanup: DatabaseCleanup,
) {
    @BeforeEach
    fun setUp() {
        databaseCleanup.execute()
    }

    @DisplayName("발급에 성공하면 FIRST_COME UserCoupon이 저장되고 발급 수량이 1 증가한다.")
    @Test
    fun savesUserCouponAndIncreasesIssuedQuantity_whenIssued() {
        val coupon = firstComeCoupon(totalQuantity = 10)

        val info = couponService.issue(CouponIssueCommand(coupon.id, userId = 1L))

        val userCoupon = userCouponJpaRepository.findByUserIdAndCouponId(1L, coupon.id)!!
        assertAll(
            { assertThat(info.userCouponId).isEqualTo(userCoupon.id) },
            { assertThat(userCoupon.grantedType).isEqualTo(UserCouponGrantedType.FIRST_COME) },
            { assertThat(couponRepository.findById(coupon.id)!!.issuedQuantity).isEqualTo(1) },
        )
    }

    @DisplayName("같은 사용자가 두 번 발급 요청하면 두 번째는 실패하고 발급 수량은 1로 유지된다.")
    @Test
    fun rejectsSecondIssue_whenSameUserIssuesTwice() {
        val coupon = firstComeCoupon(totalQuantity = 10)
        couponService.issue(CouponIssueCommand(coupon.id, userId = 1L))

        val result = assertThrows<ConflictException> {
            couponService.issue(CouponIssueCommand(coupon.id, userId = 1L))
        }

        assertAll(
            { assertThat(result.errorCode).isEqualTo(CouponErrorCode.ALREADY_ISSUED) },
            { assertThat(couponRepository.findById(coupon.id)!!.issuedQuantity).isEqualTo(1) },
            { assertThat(userCouponJpaRepository.countByCouponId(coupon.id)).isEqualTo(1) },
        )
    }

    @DisplayName("한도가 소진된 뒤 발급 요청하면 CONFLICT(SOLD_OUT) 예외가 발생한다.")
    @Test
    fun rejectsIssue_whenCouponSoldOut() {
        val coupon = firstComeCoupon(totalQuantity = 1)
        couponService.issue(CouponIssueCommand(coupon.id, userId = 1L))

        val result = assertThrows<ConflictException> {
            couponService.issue(CouponIssueCommand(coupon.id, userId = 2L))
        }

        assertThat(result.errorCode).isEqualTo(CouponErrorCode.SOLD_OUT)
    }

    @DisplayName("한도 100인 쿠폰에 300명이 동시에 요청해도 정확히 100건만 발급되고 초과분은 전부 매진 거절된다.")
    @Test
    fun issuesExactlyLimit_whenRequestedConcurrentlyBeyondLimit() {
        val coupon = firstComeCoupon(totalQuantity = 100)

        val failures = runConcurrently(threadCount = 300) { index ->
            couponService.issue(CouponIssueCommand(coupon.id, userId = (index + 1).toLong()))
        }

        assertAll(
            { assertThat(failures).hasSize(200) },
            {
                assertThat(failures).allSatisfy { failure ->
                    assertThat(failure).isInstanceOf(ConflictException::class.java)
                    assertThat((failure as ConflictException).errorCode).isEqualTo(CouponErrorCode.SOLD_OUT)
                }
            },
            { assertThat(userCouponJpaRepository.countByCouponId(coupon.id)).isEqualTo(100) },
            { assertThat(couponRepository.findById(coupon.id)!!.issuedQuantity).isEqualTo(100) },
        )
    }

    @DisplayName("같은 사용자가 동시에 여러 번 요청해도 정확히 1건만 발급된다.")
    @Test
    fun issuesOnlyOnce_whenSameUserRequestsConcurrently() {
        val coupon = firstComeCoupon(totalQuantity = 100)

        val failures = runConcurrently(threadCount = 10) {
            couponService.issue(CouponIssueCommand(coupon.id, userId = 1L))
        }

        assertAll(
            { assertThat(failures).hasSize(9) },
            { assertThat(userCouponJpaRepository.countByCouponId(coupon.id)).isEqualTo(1) },
            { assertThat(couponRepository.findById(coupon.id)!!.issuedQuantity).isEqualTo(1) },
        )
    }

    private fun firstComeCoupon(totalQuantity: Long): Coupon = couponRepository.save(
        Coupon(
            type = CouponType.FIXED,
            name = "선착순쿠폰",
            value = 1000,
            minOrderAmount = Money(0),
            expiredAt = LocalDateTime.now().plusDays(1),
            createdBy = 1L,
            totalQuantity = totalQuantity,
        ),
    )
}
