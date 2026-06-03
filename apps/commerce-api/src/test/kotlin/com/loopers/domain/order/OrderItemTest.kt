package com.loopers.domain.order

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class OrderItemTest {
    private fun item(
        productId: Long = 1L,
        quantity: Int = 1,
        snapshotProductName: String = "에어맥스",
        snapshotPrice: Long = 100_000L,
        snapshotBrandName: String = "Nike",
    ): OrderItem = OrderItem(
        productId = productId,
        quantity = quantity,
        snapshotProductName = snapshotProductName,
        snapshotPrice = snapshotPrice,
        snapshotBrandName = snapshotBrandName,
    )

    @DisplayName("생성 시 불변식")
    @Nested
    inner class Invariants {
        @DisplayName("productId 는 0보다 커야 한다.")
        @Test
        fun rejectsNonPositiveProductId() {
            assertThrows<IllegalArgumentException> { item(productId = 0L) }
            assertThrows<IllegalArgumentException> { item(productId = -1L) }
        }

        @DisplayName("quantity 는 1 이상이어야 한다.")
        @Test
        fun rejectsNonPositiveQuantity() {
            assertThrows<IllegalArgumentException> { item(quantity = 0) }
            assertThrows<IllegalArgumentException> { item(quantity = -1) }
        }

        @DisplayName("snapshotProductName 은 공백일 수 없다.")
        @Test
        fun rejectsBlankProductName() {
            assertThrows<IllegalArgumentException> { item(snapshotProductName = "") }
            assertThrows<IllegalArgumentException> { item(snapshotProductName = "   ") }
        }

        @DisplayName("snapshotPrice 는 0보다 커야 한다.")
        @Test
        fun rejectsNonPositivePrice() {
            assertThrows<IllegalArgumentException> { item(snapshotPrice = 0L) }
            assertThrows<IllegalArgumentException> { item(snapshotPrice = -1L) }
        }

        @DisplayName("snapshotBrandName 은 공백일 수 없다.")
        @Test
        fun rejectsBlankBrandName() {
            assertThrows<IllegalArgumentException> { item(snapshotBrandName = "") }
            assertThrows<IllegalArgumentException> { item(snapshotBrandName = "  ") }
        }
    }

    @DisplayName("subtotal()")
    @Nested
    inner class Subtotal {
        @DisplayName("snapshotPrice * quantity 를 반환한다.")
        @Test
        fun returnsPriceTimesQuantity() {
            val sut = item(snapshotPrice = 1_500L, quantity = 3)
            assertThat(sut.subtotal()).isEqualTo(4_500L)
        }
    }
}
