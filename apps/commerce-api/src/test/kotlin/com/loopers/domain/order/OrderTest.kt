package com.loopers.domain.order

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.ZonedDateTime

class OrderTest {
    private val fixedAt = ZonedDateTime.parse("2026-01-01T12:00:00+09:00[Asia/Seoul]")

    private fun item(productId: Long = 1L, quantity: Int = 1, price: Long = 1_000L): OrderItem =
        OrderItem(
            productId = productId,
            quantity = quantity,
            snapshotProductName = "p$productId",
            snapshotPrice = price,
            snapshotBrandName = "Nike",
        )

    private fun items(vararg list: OrderItem): OrderItems = OrderItems(list.toList())

    private fun appliedCoupon(discountAmount: Long, issuedCouponId: Long = 1L): AppliedCoupon =
        AppliedCoupon(
            issuedCouponId = issuedCouponId,
            couponName = "5천원 할인",
            couponType = "FIXED",
            couponValue = discountAmount,
            discountAmount = discountAmount,
        )

    @DisplayName("Order.create")
    @Nested
    inner class Create {
        @DisplayName("status=CREATED, usedPoint=0, totalAmount=Σ(snapshotPrice*qty) 로 생성된다.")
        @Test
        fun setsDefaults() {
            val order = Order.create(
                userId = 1L,
                items = items(item(productId = 1L, quantity = 2, price = 1_000L), item(productId = 2L, quantity = 1, price = 3_000L)),
                orderedAt = fixedAt,
            )

            assertThat(order.userId).isEqualTo(1L)
            assertThat(order.status).isEqualTo(OrderStatus.CREATED)
            assertThat(order.usedPoint).isEqualTo(0L)
            assertThat(order.totalAmount).isEqualTo(5_000L)
            assertThat(order.orderedAt).isEqualTo(fixedAt)
            assertThat(order.items.size).isEqualTo(2)
        }

        @DisplayName("userId 가 0 이하면 생성에 실패한다.")
        @Test
        fun rejectsNonPositiveUserId() {
            assertThrows<IllegalArgumentException> {
                Order.create(userId = 0L, items = items(item()), orderedAt = fixedAt)
            }
        }

        @DisplayName("items 가 비어 있으면 OrderItems 단계에서 실패한다.")
        @Test
        fun rejectsEmptyItems() {
            assertThrows<IllegalArgumentException> {
                Order.create(userId = 1L, items = OrderItems(emptyList()), orderedAt = fixedAt)
            }
        }
    }

    @DisplayName("getActualAmount()")
    @Nested
    inner class ActualAmount {
        @DisplayName("totalAmount - usedPoint 를 반환한다 (usedPoint=0 이번 주차 기본).")
        @Test
        fun returnsTotalMinusUsedPoint() {
            val order = Order.create(
                userId = 1L,
                items = items(item(quantity = 5, price = 1_000L)),
                orderedAt = fixedAt,
            )

            assertThat(order.getActualAmount()).isEqualTo(5_000L)
        }

        @DisplayName("쿠폰이 적용되면 totalAmount - discountAmount - usedPoint 를 반환한다.")
        @Test
        fun reflectsCouponDiscount() {
            val order = Order.create(
                userId = 1L,
                items = items(item(quantity = 5, price = 1_000L)),
                appliedCoupon = appliedCoupon(discountAmount = 1_500L),
                orderedAt = fixedAt,
            )

            assertThat(order.discountAmount).isEqualTo(1_500L)
            assertThat(order.getActualAmount()).isEqualTo(3_500L)
        }

        @DisplayName("쿠폰이 없으면 discountAmount 는 0 이다.")
        @Test
        fun zeroDiscount_whenNoCoupon() {
            val order = Order.create(userId = 1L, items = items(item(price = 1_000L)), orderedAt = fixedAt)

            assertThat(order.discountAmount).isEqualTo(0L)
            assertThat(order.getActualAmount()).isEqualTo(1_000L)
        }
    }

    @DisplayName("쿠폰 할인 불변식")
    @Nested
    inner class CouponInvariant {
        @DisplayName("할인 금액이 총 주문 금액을 초과하면 생성에 실패한다.")
        @Test
        fun rejectsDiscountOverTotal() {
            assertThrows<IllegalArgumentException> {
                Order.create(
                    userId = 1L,
                    items = items(item(quantity = 1, price = 1_000L)),
                    appliedCoupon = appliedCoupon(discountAmount = 1_500L),
                    orderedAt = fixedAt,
                )
            }
        }

        @DisplayName("할인 금액이 총 주문 금액과 같으면 최종 금액 0 으로 생성된다.")
        @Test
        fun allowsDiscountEqualToTotal() {
            val order = Order.create(
                userId = 1L,
                items = items(item(quantity = 1, price = 1_000L)),
                appliedCoupon = appliedCoupon(discountAmount = 1_000L),
                orderedAt = fixedAt,
            )

            assertThat(order.getActualAmount()).isEqualTo(0L)
        }
    }

    @DisplayName("getEarnPoint()")
    @Nested
    inner class EarnPoint {
        @DisplayName("PAYMENT_COMPLETED/SHIPPING/DELIVERED 에서 actualAmount * 1% 의 floor 를 반환한다.")
        @ParameterizedTest
        @EnumSource(value = OrderStatus::class, names = ["PAYMENT_COMPLETED", "SHIPPING", "DELIVERED"])
        fun returnsOnePercent_whenEarnStatus(status: OrderStatus) {
            val base = Order.create(
                userId = 1L,
                items = items(item(quantity = 1, price = 12_345L)),
                orderedAt = fixedAt,
            )
            val transitioned = base.copy(status = status)

            assertThat(transitioned.getEarnPoint()).isEqualTo(123L)
        }

        @DisplayName("그 외 상태에서는 0 을 반환한다.")
        @ParameterizedTest
        @EnumSource(value = OrderStatus::class, names = ["CREATED", "PAYMENT_PENDING", "CANCELLED"])
        fun returnsZero_otherwise(status: OrderStatus) {
            val base = Order.create(
                userId = 1L,
                items = items(item(quantity = 1, price = 12_345L)),
                orderedAt = fixedAt,
            )
            val transitioned = base.copy(status = status)

            assertThat(transitioned.getEarnPoint()).isEqualTo(0L)
        }
    }

    @DisplayName("updateStatus(next)")
    @Nested
    inner class UpdateStatus {
        @DisplayName("허용 전이는 status 가 갱신된 새 Order 를 반환한다.")
        @Test
        fun appliesAllowedTransition() {
            val order = Order.create(userId = 1L, items = items(item()), orderedAt = fixedAt)

            val next = order.updateStatus(OrderStatus.PAYMENT_PENDING)

            assertThat(next.status).isEqualTo(OrderStatus.PAYMENT_PENDING)
            assertThat(order.status).isEqualTo(OrderStatus.CREATED)
        }

        @DisplayName("허용되지 않는 전이는 예외를 던진다.")
        @Test
        fun rejectsForbiddenTransition() {
            val order = Order.create(userId = 1L, items = items(item()), orderedAt = fixedAt)

            assertThrows<IllegalArgumentException> { order.updateStatus(OrderStatus.PAYMENT_COMPLETED) }
            assertThrows<IllegalArgumentException> { order.updateStatus(OrderStatus.SHIPPING) }
            assertThrows<IllegalArgumentException> { order.updateStatus(OrderStatus.DELIVERED) }
        }
    }

    @DisplayName("cancel()")
    @Nested
    inner class Cancel {
        @DisplayName("CREATED 또는 PAYMENT_PENDING 에서 CANCELLED 로 전환된다.")
        @ParameterizedTest
        @EnumSource(value = OrderStatus::class, names = ["CREATED", "PAYMENT_PENDING"])
        fun cancelsFromAllowed(status: OrderStatus) {
            val base = Order.create(userId = 1L, items = items(item()), orderedAt = fixedAt)
            val target = base.copy(status = status)

            val cancelled = target.cancel()

            assertThat(cancelled.status).isEqualTo(OrderStatus.CANCELLED)
        }

        @DisplayName("그 외 상태에서 cancel 호출 시 예외가 발생한다.")
        @ParameterizedTest
        @EnumSource(value = OrderStatus::class, names = ["PAYMENT_COMPLETED", "SHIPPING", "DELIVERED", "CANCELLED"])
        fun rejectsCancelFromOther(status: OrderStatus) {
            val base = Order.create(userId = 1L, items = items(item()), orderedAt = fixedAt)
            val target = base.copy(status = status)

            assertThrows<IllegalArgumentException> { target.cancel() }
        }
    }
}
