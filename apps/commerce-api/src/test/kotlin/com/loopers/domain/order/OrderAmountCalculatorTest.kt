package com.loopers.domain.order

import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.DiscountPolicy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

class OrderAmountCalculatorTest {
    @DisplayName("주문 금액 계산 시, ")
    @Nested
    inner class Calculate {
        @DisplayName("쿠폰이 없으면 할인 전 총액과 최종 결제 금액이 같다.")
        @Test
        fun calculatesWithoutCoupon() {
            // arrange
            val items = listOf(
                newOrderItem(productId = 1L, productPrice = OrderItemPrice(10_000L), quantity = OrderQuantity(2)),
                newOrderItem(productId = 2L, productPrice = OrderItemPrice(5_000L), quantity = OrderQuantity(1)),
            )

            // act
            val amounts = OrderAmountCalculator.calculate(items = items)

            // assert
            assertAll(
                { assertThat(amounts.totalAmount).isEqualTo(OrderAmount(25_000L)) },
                { assertThat(amounts.discountAmount).isEqualTo(OrderAmount.ZERO) },
                { assertThat(amounts.paymentAmount).isEqualTo(OrderAmount(25_000L)) },
            )
        }

        @DisplayName("정액 쿠폰이 있으면 할인액을 차감해 최종 결제 금액을 계산한다.")
        @Test
        fun calculatesWithFixedAmountCoupon() {
            // arrange
            val items = listOf(newOrderItem(productPrice = OrderItemPrice(10_000L), quantity = OrderQuantity(2)))
            val coupon = Coupon(name = "1000원 할인", policy = DiscountPolicy.FixedAmount(1_000L))

            // act
            val amounts = OrderAmountCalculator.calculate(items = items, coupon = coupon)

            // assert
            assertAll(
                { assertThat(amounts.totalAmount).isEqualTo(OrderAmount(20_000L)) },
                { assertThat(amounts.discountAmount).isEqualTo(OrderAmount(1_000L)) },
                { assertThat(amounts.paymentAmount).isEqualTo(OrderAmount(19_000L)) },
            )
        }

        @DisplayName("정률 쿠폰이 있으면 비율 할인액을 차감해 최종 결제 금액을 계산한다.")
        @Test
        fun calculatesWithRateCoupon() {
            // arrange
            val items = listOf(newOrderItem(productPrice = OrderItemPrice(10_000L), quantity = OrderQuantity(3)))
            val coupon = Coupon(name = "10% 할인", policy = DiscountPolicy.Rate(10))

            // act
            val amounts = OrderAmountCalculator.calculate(items = items, coupon = coupon)

            // assert
            assertAll(
                { assertThat(amounts.totalAmount).isEqualTo(OrderAmount(30_000L)) },
                { assertThat(amounts.discountAmount).isEqualTo(OrderAmount(3_000L)) },
                { assertThat(amounts.paymentAmount).isEqualTo(OrderAmount(27_000L)) },
            )
        }
    }

    private fun newOrderItem(
        productId: Long = 1L,
        productName: String = "Loopers T-Shirt",
        productPrice: OrderItemPrice = OrderItemPrice(10_000L),
        quantity: OrderQuantity = OrderQuantity(1),
    ) = OrderItem(
        productSnapshot = ProductSnapshot(
            productId = productId,
            productName = productName,
            productPrice = productPrice,
        ),
        quantity = quantity,
    )
}
