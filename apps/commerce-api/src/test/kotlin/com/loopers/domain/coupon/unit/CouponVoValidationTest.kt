package com.loopers.domain.coupon.unit

import com.loopers.domain.coupon.exception.InvalidCouponException
import com.loopers.domain.coupon.support.CouponSteps.Companion.기본_쿠폰명
import com.loopers.domain.coupon.vo.CouponName
import com.loopers.domain.coupon.vo.FixedAmountDiscountPolicy
import com.loopers.domain.coupon.vo.PercentageDiscountPolicy
import com.loopers.domain.product.vo.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CouponVoValidationTest {
    @Test
    fun `쿠폰명은_공백이_아닌_문자가_있으면_생성된다`() {
        assertThat(CouponName.of(기본_쿠폰명).value).isEqualTo(기본_쿠폰명)
    }

    @Test
    fun `쿠폰명은_공백만_있으면_생성이_불가하다`() {
        assertThrows<InvalidCouponException> { CouponName.of("   ") }
    }

    @Test
    fun `정액_할인은_양수_금액이면_생성된다`() {
        val policy = FixedAmountDiscountPolicy.of(Money.of(1_000))

        assertThat(policy.amount.value).isEqualTo(1_000)
    }

    @Test
    fun `정액_할인은_0원이면_생성이_불가하다`() {
        assertThrows<InvalidCouponException> {
            FixedAmountDiscountPolicy.of(Money.of(0))
        }
    }

    @Test
    fun `정률_할인은_1부터_100까지_생성된다`() {
        assertThat(PercentageDiscountPolicy.of(1).percent).isEqualTo(1)
        assertThat(PercentageDiscountPolicy.of(100).percent).isEqualTo(100)
    }

    @Test
    fun `정률_할인은_범위를_벗어나면_생성이_불가하다`() {
        assertThrows<InvalidCouponException> { PercentageDiscountPolicy.of(0) }
        assertThrows<InvalidCouponException> { PercentageDiscountPolicy.of(101) }
    }
}
