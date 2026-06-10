package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class CouponTest {

    @DisplayName("쿠폰 생성 시, ")
    @Nested
    inner class Create {
        @DisplayName("이름과 정책이 유효하면 정상적으로 생성된다.")
        @Test
        fun create_whenAllFieldsAreValid() {
            // act
            val coupon = Coupon(name = "신규가입 1000원 할인", policy = DiscountPolicy.FixedAmount(1_000L))

            // assert
            assertThat(coupon.name).isEqualTo("신규가입 1000원 할인")
            assertThat(coupon.policy).isInstanceOf(DiscountPolicy.FixedAmount::class.java)
        }

        @DisplayName("이름이 비어있거나 공백이면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["", " ", "   "])
        fun throwsBadRequest_whenNameIsBlank(name: String) {
            // act & assert
            val result = assertThrows<CoreException> {
                Coupon(name = name, policy = DiscountPolicy.FixedAmount(1_000L))
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("할인 금액 계산 시, ")
    @Nested
    inner class DiscountOf {
        @DisplayName("정액 쿠폰은 정액 정책의 할인 금액을 정상적으로 반환한다.")
        @Test
        fun delegatesToFixedAmountPolicy() {
            // arrange
            val coupon = Coupon(name = "1000원 할인", policy = DiscountPolicy.FixedAmount(1_000L))

            // act
            val discount = coupon.discountOf(DiscountAmount(5_000L))

            // assert
            assertThat(discount).isEqualTo(DiscountAmount(1_000L))
        }

        @DisplayName("정률 쿠폰은 정률 정책의 할인 금액을 정상적으로 반환한다.")
        @Test
        fun delegatesToRatePolicy() {
            // arrange
            val coupon = Coupon(name = "10% 할인", policy = DiscountPolicy.Rate(10))

            // act
            val discount = coupon.discountOf(DiscountAmount(10_000L))

            // assert
            assertThat(discount).isEqualTo(DiscountAmount(1_000L))
        }
    }

    @DisplayName("쿠폰 이름 변경 시, ")
    @Nested
    inner class Rename {
        @DisplayName("유효한 이름이면 정상적으로 변경된다.")
        @Test
        fun rename_whenNameIsValid() {
            // arrange
            val coupon = Coupon(name = "초기 이름", policy = DiscountPolicy.FixedAmount(1_000L))

            // act
            coupon.rename("새 이름")

            // assert
            assertThat(coupon.name).isEqualTo("새 이름")
        }

        @DisplayName("빈 이름으로 변경하려고 하면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["", " ", "   "])
        fun throwsBadRequest_whenNameIsBlank(blankName: String) {
            // arrange
            val coupon = Coupon(name = "초기 이름", policy = DiscountPolicy.FixedAmount(1_000L))

            // act & assert
            val result = assertThrows<CoreException> { coupon.rename(blankName) }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}
