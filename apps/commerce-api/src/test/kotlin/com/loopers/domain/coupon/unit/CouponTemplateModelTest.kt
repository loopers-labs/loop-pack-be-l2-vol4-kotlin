package com.loopers.domain.coupon.unit

import com.loopers.domain.coupon.exception.CouponNotUsableException
import com.loopers.domain.coupon.support.CouponSteps.Companion.기준_시각
import com.loopers.domain.coupon.support.CouponSteps.Companion.기본_만료시각
import com.loopers.domain.coupon.support.CouponSteps.Companion.정률_할인정책
import com.loopers.domain.coupon.support.CouponSteps.Companion.쿠폰템플릿_도메인_생성
import com.loopers.domain.product.vo.Money
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CouponTemplateModelTest {
    @Test
    fun `사용_가능한_쿠폰은_할인금액을_계산한다`() {
        val template = 쿠폰템플릿_도메인_생성(
            discountPolicy = 정률_할인정책(percent = 10),
            minOrderAmount = 10_000,
        )

        val discount = template.calculateDiscount(Money.of(20_000))

        assertThat(discount.value).isEqualTo(2_000)
    }

    @Test
    fun `최소_주문금액을_만족하면_사용_가능하다`() {
        val template = 쿠폰템플릿_도메인_생성(minOrderAmount = 10_000)

        assertThatCode {
            template.requireUsable(totalPrice = Money.of(10_000), now = 기준_시각)
        }.doesNotThrowAnyException()
    }

    @Test
    fun `최소_주문금액보다_작으면_사용_불가하다`() {
        val template = 쿠폰템플릿_도메인_생성(minOrderAmount = 10_000)

        assertThrows<CouponNotUsableException> {
            template.requireUsable(totalPrice = Money.of(9_999), now = 기준_시각)
        }
    }

    @Test
    fun `만료시각_이후면_사용_불가하다`() {
        val template = 쿠폰템플릿_도메인_생성(expiredAt = 기본_만료시각)

        assertThrows<CouponNotUsableException> {
            template.requireUsable(totalPrice = Money.of(10_000), now = 기본_만료시각)
        }
    }

    @Test
    fun `삭제된_쿠폰은_사용_불가하다`() {
        val template = 쿠폰템플릿_도메인_생성().delete(기준_시각)

        assertThrows<CouponNotUsableException> {
            template.requireUsable(totalPrice = Money.of(10_000), now = 기준_시각)
        }
    }

    @Test
    fun `쿠폰_삭제는_멱등하다`() {
        val template = 쿠폰템플릿_도메인_생성()

        val deleted = template.delete(기준_시각)
        val deletedAgain = deleted.delete(기준_시각.plusDays(1))

        assertThat(deleted.deletedAtOrNull).isEqualTo(기준_시각)
        assertThat(deletedAgain.deletedAtOrNull).isEqualTo(기준_시각)
    }
}
