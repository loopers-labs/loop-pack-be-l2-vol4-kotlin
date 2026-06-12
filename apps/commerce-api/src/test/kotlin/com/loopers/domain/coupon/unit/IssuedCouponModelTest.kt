package com.loopers.domain.coupon.unit

import com.loopers.domain.coupon.exception.CouponNotOwnedException
import com.loopers.domain.coupon.exception.IssuedCouponNotAvailableException
import com.loopers.domain.coupon.model.IssuedCouponDisplayStatus
import com.loopers.domain.coupon.model.IssuedCouponStatus
import com.loopers.domain.coupon.support.CouponSteps.Companion.기준_시각
import com.loopers.domain.coupon.support.CouponSteps.Companion.기본_만료시각
import com.loopers.domain.coupon.support.CouponSteps.Companion.발급쿠폰_도메인_생성
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class IssuedCouponModelTest {
    @Test
    fun `발급_쿠폰은_AVAILABLE_상태로_생성된다`() {
        val issuedCoupon = 발급쿠폰_도메인_생성()

        assertThat(issuedCoupon.status).isEqualTo(IssuedCouponStatus.AVAILABLE)
        assertThat(issuedCoupon.usedAt).isNull()
    }

    @Test
    fun `소유자가_일치하면_검증에_성공한다`() {
        val issuedCoupon = 발급쿠폰_도메인_생성(userId = 1L)

        assertThatCode {
            issuedCoupon.requireOwnedBy(1L)
        }.doesNotThrowAnyException()
    }

    @Test
    fun `소유자가_다르면_검증에_실패한다`() {
        val issuedCoupon = 발급쿠폰_도메인_생성(userId = 1L)

        assertThrows<CouponNotOwnedException> {
            issuedCoupon.requireOwnedBy(2L)
        }
    }

    @Test
    fun `사용_가능한_발급쿠폰은_USED_상태로_전환된다`() {
        val issuedCoupon = 발급쿠폰_도메인_생성()

        val used = issuedCoupon.use(기준_시각.plusHours(1))

        assertThat(used.status).isEqualTo(IssuedCouponStatus.USED)
        assertThat(used.usedAt).isEqualTo(기준_시각.plusHours(1))
    }

    @Test
    fun `이미_사용한_발급쿠폰은_다시_사용할_수_없다`() {
        val used = 발급쿠폰_도메인_생성().use(기준_시각)

        assertThrows<IssuedCouponNotAvailableException> {
            used.use(기준_시각.plusHours(1))
        }
    }

    @Test
    fun `사용_복구는_USED를_AVAILABLE로_되돌린다`() {
        val used = 발급쿠폰_도메인_생성().use(기준_시각)

        val reverted = used.revertUse()

        assertThat(reverted.status).isEqualTo(IssuedCouponStatus.AVAILABLE)
        assertThat(reverted.usedAt).isNull()
    }

    @Test
    fun `표시상태는_USED가_저장상태를_우선한다`() {
        val used = 발급쿠폰_도메인_생성().use(기준_시각)

        val status = used.displayStatus(expiredAt = 기본_만료시각, now = 기본_만료시각.plusSeconds(1))

        assertThat(status).isEqualTo(IssuedCouponDisplayStatus.USED)
    }

    @Test
    fun `표시상태는_AVAILABLE이고_만료시각_이후면_EXPIRED다`() {
        val issuedCoupon = 발급쿠폰_도메인_생성()

        val status = issuedCoupon.displayStatus(expiredAt = 기본_만료시각, now = 기본_만료시각)

        assertThat(status).isEqualTo(IssuedCouponDisplayStatus.EXPIRED)
    }
}
