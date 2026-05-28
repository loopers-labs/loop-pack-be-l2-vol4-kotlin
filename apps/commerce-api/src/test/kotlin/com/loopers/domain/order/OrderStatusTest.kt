package com.loopers.domain.order

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class OrderStatusTest {
    @DisplayName("isIncomplete()")
    @Nested
    inner class IsIncomplete {
        @DisplayName("CREATED / PAYMENT_PENDING / PAYMENT_COMPLETED / SHIPPING 은 미완료이다.")
        @ParameterizedTest
        @EnumSource(value = OrderStatus::class, names = ["CREATED", "PAYMENT_PENDING", "PAYMENT_COMPLETED", "SHIPPING"])
        fun returnsTrue_whenIncomplete(status: OrderStatus) {
            assertThat(status.isIncomplete()).isTrue()
        }

        @DisplayName("DELIVERED / CANCELLED 는 완료 상태이다.")
        @ParameterizedTest
        @EnumSource(value = OrderStatus::class, names = ["DELIVERED", "CANCELLED"])
        fun returnsFalse_whenCompleted(status: OrderStatus) {
            assertThat(status.isIncomplete()).isFalse()
        }
    }

    @DisplayName("canTransitionTo(next)")
    @Nested
    inner class CanTransitionTo {
        @DisplayName("CREATED 는 PAYMENT_PENDING 으로만 전이 가능하다.")
        @Test
        fun fromCreated() {
            assertThat(OrderStatus.CREATED.canTransitionTo(OrderStatus.PAYMENT_PENDING)).isTrue()
            OrderStatus.values()
                .filter { it != OrderStatus.PAYMENT_PENDING }
                .forEach { assertThat(OrderStatus.CREATED.canTransitionTo(it)).isFalse() }
        }

        @DisplayName("PAYMENT_PENDING 은 PAYMENT_COMPLETED 또는 CANCELLED 로 전이 가능하다.")
        @Test
        fun fromPaymentPending() {
            assertThat(OrderStatus.PAYMENT_PENDING.canTransitionTo(OrderStatus.PAYMENT_COMPLETED)).isTrue()
            assertThat(OrderStatus.PAYMENT_PENDING.canTransitionTo(OrderStatus.CANCELLED)).isTrue()
            OrderStatus.values()
                .filter { it != OrderStatus.PAYMENT_COMPLETED && it != OrderStatus.CANCELLED }
                .forEach { assertThat(OrderStatus.PAYMENT_PENDING.canTransitionTo(it)).isFalse() }
        }

        @DisplayName("PAYMENT_COMPLETED 는 SHIPPING 으로만 전이 가능하다.")
        @Test
        fun fromPaymentCompleted() {
            assertThat(OrderStatus.PAYMENT_COMPLETED.canTransitionTo(OrderStatus.SHIPPING)).isTrue()
            OrderStatus.values()
                .filter { it != OrderStatus.SHIPPING }
                .forEach { assertThat(OrderStatus.PAYMENT_COMPLETED.canTransitionTo(it)).isFalse() }
        }

        @DisplayName("SHIPPING 은 DELIVERED 로만 전이 가능하다.")
        @Test
        fun fromShipping() {
            assertThat(OrderStatus.SHIPPING.canTransitionTo(OrderStatus.DELIVERED)).isTrue()
            OrderStatus.values()
                .filter { it != OrderStatus.DELIVERED }
                .forEach { assertThat(OrderStatus.SHIPPING.canTransitionTo(it)).isFalse() }
        }

        @DisplayName("DELIVERED / CANCELLED 는 어떤 상태로도 전이할 수 없다.")
        @ParameterizedTest
        @EnumSource(value = OrderStatus::class, names = ["DELIVERED", "CANCELLED"])
        fun fromTerminal(terminal: OrderStatus) {
            OrderStatus.values().forEach {
                assertThat(terminal.canTransitionTo(it)).isFalse()
            }
        }
    }
}
