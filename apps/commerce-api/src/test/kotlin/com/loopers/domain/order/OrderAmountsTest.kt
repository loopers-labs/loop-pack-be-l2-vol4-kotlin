package com.loopers.domain.order

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

class OrderAmountsTest {
    @DisplayName("주문 금액 생성 시, ")
    @Nested
    inner class Create {
        @DisplayName("총액과 할인액이 유효하면 최종 결제 금액을 계산한다.")
        @Test
        fun create_whenAmountsAreValid() {
            // act
            val amounts = OrderAmounts.of(
                totalAmount = OrderAmount(25_000L),
                discountAmount = OrderAmount(2_500L),
            )

            // assert
            assertAll(
                { assertThat(amounts.totalAmount).isEqualTo(OrderAmount(25_000L)) },
                { assertThat(amounts.discountAmount).isEqualTo(OrderAmount(2_500L)) },
                { assertThat(amounts.paymentAmount).isEqualTo(OrderAmount(22_500L)) },
            )
        }

        @DisplayName("할인액을 생략하면 할인액은 0원이다.")
        @Test
        fun create_whenDiscountAmountIsOmitted() {
            // act
            val amounts = OrderAmounts.of(totalAmount = OrderAmount(10_000L))

            // assert
            assertAll(
                { assertThat(amounts.discountAmount).isEqualTo(OrderAmount.ZERO) },
                { assertThat(amounts.paymentAmount).isEqualTo(OrderAmount(10_000L)) },
            )
        }

        @DisplayName("할인액이 총액보다 크면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenDiscountAmountIsGreaterThanTotalAmount() {
            // act & assert
            val result = assertThrows<CoreException> {
                OrderAmounts.of(
                    totalAmount = OrderAmount(1_000L),
                    discountAmount = OrderAmount(1_001L),
                )
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("최종 결제 금액이 총액에서 할인액을 뺀 값과 다르면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenPaymentAmountDoesNotMatch() {
            // act & assert
            val result = assertThrows<CoreException> {
                OrderAmounts(
                    totalAmount = OrderAmount(10_000L),
                    discountAmount = OrderAmount(1_000L),
                    paymentAmount = OrderAmount(8_000L),
                )
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}
