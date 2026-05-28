package com.loopers.domain.order

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class OrderItemsTest {
    private fun item(
        productId: Long = 1L,
        quantity: Int = 1,
        snapshotPrice: Long = 100L,
    ): OrderItem = OrderItem(
        productId = productId,
        quantity = quantity,
        snapshotProductName = "p$productId",
        snapshotPrice = snapshotPrice,
        snapshotBrandName = "Nike",
    )

    @DisplayName("생성 시 불변식")
    @Nested
    inner class Invariants {
        @DisplayName("빈 리스트로 생성하면 예외가 발생한다.")
        @Test
        fun rejectsEmpty() {
            assertThrows<IllegalArgumentException> { OrderItems(emptyList()) }
        }

        @DisplayName("최소 1개 항목이 있으면 정상 생성된다.")
        @Test
        fun acceptsSingleItem() {
            val sut = OrderItems(listOf(item()))
            assertThat(sut.size).isEqualTo(1)
        }
    }

    @DisplayName("totalAmount()")
    @Nested
    inner class TotalAmount {
        @DisplayName("모든 항목의 subtotal 합계를 반환한다.")
        @Test
        fun sumsAllSubtotals() {
            val sut = OrderItems(
                listOf(
                    item(productId = 1L, quantity = 2, snapshotPrice = 1_000L), // 2,000
                    item(productId = 2L, quantity = 3, snapshotPrice = 500L), //   1,500
                    item(productId = 3L, quantity = 1, snapshotPrice = 4_000L), // 4,000
                ),
            )

            assertThat(sut.totalAmount()).isEqualTo(7_500L)
        }
    }

    @DisplayName("productIds()")
    @Nested
    inner class ProductIds {
        @DisplayName("입력 순서대로 productId 리스트를 반환한다.")
        @Test
        fun returnsProductIdsInOrder() {
            val sut = OrderItems(
                listOf(
                    item(productId = 10L),
                    item(productId = 20L),
                    item(productId = 30L),
                ),
            )

            assertThat(sut.productIds()).containsExactly(10L, 20L, 30L)
        }

        @DisplayName("동일 productId 중복도 그대로 보존한다.")
        @Test
        fun preservesDuplicates() {
            val sut = OrderItems(
                listOf(
                    item(productId = 7L),
                    item(productId = 7L),
                ),
            )

            assertThat(sut.productIds()).containsExactly(7L, 7L)
        }
    }
}
