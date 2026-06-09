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

class DiscountPolicyTest {

    @DisplayName("정액 할인 정책(FixedAmount) 생성 시, ")
    @Nested
    inner class CreateFixedAmount {
        @DisplayName("할인액이 양수이면 정상적으로 생성된다.")
        @Test
        fun create_whenAmountIsPositive() {
            // act
            val policy = DiscountPolicy.FixedAmount(1_000L)

            // assert
            assertThat(policy.amount).isEqualTo(1_000L)
        }

        @DisplayName("할인액이 0 이하이면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(longs = [0L, -1L, -1_000L])
        fun throwsBadRequest_whenAmountIsNotPositive(amount: Long) {
            // act & assert
            val result = assertThrows<CoreException> { DiscountPolicy.FixedAmount(amount) }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("정률 할인 정책(Rate) 생성 시, ")
    @Nested
    inner class CreateRate {
        @DisplayName("할인율이 0~100 범위면 정상적으로 생성된다.")
        @ParameterizedTest
        @ValueSource(ints = [0, 1, 50, 99, 100])
        fun create_whenPercentInValidRange(percent: Int) {
            // act
            val policy = DiscountPolicy.Rate(percent)

            // assert
            assertThat(policy.percent).isEqualTo(percent)
        }

        @DisplayName("할인율이 0보다 작거나 100보다 크면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(ints = [-1, -100, 101, 1_000])
        fun throwsBadRequest_whenPercentOutOfRange(percent: Int) {
            // act & assert
            val result = assertThrows<CoreException> { DiscountPolicy.Rate(percent) }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("정액 할인 적용 시, ")
    @Nested
    inner class FixedAmountDiscountOf {
        @DisplayName("대상 금액이 할인액보다 크면 할인액 만큼 반환한다.")
        @Test
        fun returnsFixedAmount_whenTargetIsGreaterThanFixed() {
            // arrange
            val policy = DiscountPolicy.FixedAmount(1_000L)

            // act
            val discount = policy.discountOf(DiscountAmount(5_000L))

            // assert
            assertThat(discount).isEqualTo(DiscountAmount(1_000L))
        }

        @DisplayName("대상 금액이 할인액보다 작으면 대상 금액 만큼만 할인한다.")
        @Test
        fun returnsTargetAmount_whenTargetIsLessThanFixed() {
            // arrange
            val policy = DiscountPolicy.FixedAmount(10_000L)

            // act
            val discount = policy.discountOf(DiscountAmount(3_000L))

            // assert
            assertThat(discount).isEqualTo(DiscountAmount(3_000L))
        }

        @DisplayName("대상 금액과 할인액이 같으면 대상 금액 만큼 할인한다.")
        @Test
        fun returnsTargetAmount_whenTargetEqualsFixed() {
            // arrange
            val policy = DiscountPolicy.FixedAmount(2_000L)

            // act
            val discount = policy.discountOf(DiscountAmount(2_000L))

            // assert
            assertThat(discount).isEqualTo(DiscountAmount(2_000L))
        }
    }

    @DisplayName("정률 할인 적용 시, ")
    @Nested
    inner class RateDiscountOf {
        @DisplayName("대상 금액에 할인율을 곱한 금액을 반환한다.")
        @Test
        fun returnsAmountTimesPercent() {
            // arrange
            val policy = DiscountPolicy.Rate(10)

            // act
            val discount = policy.discountOf(DiscountAmount(10_000L))

            // assert
            assertThat(discount).isEqualTo(DiscountAmount(1_000L))
        }

        @DisplayName("할인율이 0이면 할인 금액은 0이다.")
        @Test
        fun returnsZero_whenPercentIsZero() {
            // arrange
            val policy = DiscountPolicy.Rate(0)

            // act
            val discount = policy.discountOf(DiscountAmount(10_000L))

            // assert
            assertThat(discount).isEqualTo(DiscountAmount.ZERO)
        }

        @DisplayName("할인율이 100이면 대상 금액 전체를 할인한다.")
        @Test
        fun returnsTargetAmount_whenPercentIs100() {
            // arrange
            val policy = DiscountPolicy.Rate(100)

            // act
            val discount = policy.discountOf(DiscountAmount(7_777L))

            // assert
            assertThat(discount).isEqualTo(DiscountAmount(7_777L))
        }

        @DisplayName("나누어 떨어지지 않는 경우 소수점은 버린다.")
        @Test
        fun truncatesFraction() {
            // arrange
            val policy = DiscountPolicy.Rate(33)

            // act
            val discount = policy.discountOf(DiscountAmount(100L))

            // assert (100 * 33 / 100 = 33)
            assertThat(discount).isEqualTo(DiscountAmount(33L))
        }
    }
}
