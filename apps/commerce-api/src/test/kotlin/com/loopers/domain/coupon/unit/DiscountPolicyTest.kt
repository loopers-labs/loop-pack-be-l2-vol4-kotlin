package com.loopers.domain.coupon.unit

import com.loopers.domain.coupon.support.CouponSteps.Companion.정률_할인정책
import com.loopers.domain.coupon.support.CouponSteps.Companion.정액_할인정책
import com.loopers.domain.product.vo.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DiscountPolicyTest {
    @Test
    fun `정액_할인은_주문금액에서_고정금액을_할인한다`() {
        val discount = 정액_할인정책(amount = 1_000).calculate(Money.of(10_000))

        assertThat(discount.value).isEqualTo(1_000)
    }

    @Test
    fun `정액_할인은_주문금액을_초과할_수_없다`() {
        val discount = 정액_할인정책(amount = 20_000).calculate(Money.of(10_000))

        assertThat(discount.value).isEqualTo(10_000)
    }

    @Test
    fun `정률_할인은_원_단위_버림으로_계산한다`() {
        val discount = 정률_할인정책(percent = 15).calculate(Money.of(9_999))

        assertThat(discount.value).isEqualTo(1_499)
    }

    @Test
    fun `정률_할인은_주문금액을_초과할_수_없다`() {
        val discount = 정률_할인정책(percent = 100).calculate(Money.of(10_000))

        assertThat(discount.value).isEqualTo(10_000)
    }
}
