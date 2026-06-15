package com.loopers.domain.order

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AppliedCouponTest {
    private fun appliedCoupon(
        issuedCouponId: Long = 1L,
        couponName: String = "5천원 할인",
        couponType: String = "FIXED",
        couponValue: Long = 5_000L,
        discountAmount: Long = 5_000L,
    ): AppliedCoupon = AppliedCoupon(
        issuedCouponId = issuedCouponId,
        couponName = couponName,
        couponType = couponType,
        couponValue = couponValue,
        discountAmount = discountAmount,
    )

    @DisplayName("정상 값으로 스냅샷이 생성된다.")
    @Test
    fun createsSnapshot() {
        val snapshot = appliedCoupon(discountAmount = 3_000L)

        assertThat(snapshot.issuedCouponId).isEqualTo(1L)
        assertThat(snapshot.couponType).isEqualTo("FIXED")
        assertThat(snapshot.discountAmount).isEqualTo(3_000L)
    }

    @DisplayName("issuedCouponId 가 0 이하면 생성에 실패한다.")
    @Test
    fun rejectsNonPositiveId() {
        assertThrows<IllegalArgumentException> { appliedCoupon(issuedCouponId = 0L) }
    }

    @DisplayName("couponName 이 비어 있으면 생성에 실패한다.")
    @Test
    fun rejectsBlankName() {
        assertThrows<IllegalArgumentException> { appliedCoupon(couponName = " ") }
    }

    @DisplayName("couponType 이 비어 있으면 생성에 실패한다.")
    @Test
    fun rejectsBlankType() {
        assertThrows<IllegalArgumentException> { appliedCoupon(couponType = "") }
    }

    @DisplayName("discountAmount 가 음수면 생성에 실패한다.")
    @Test
    fun rejectsNegativeDiscount() {
        assertThrows<IllegalArgumentException> { appliedCoupon(discountAmount = -1L) }
    }
}
