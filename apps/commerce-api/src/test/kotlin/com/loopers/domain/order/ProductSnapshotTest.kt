package com.loopers.domain.order

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

class ProductSnapshotTest {
    @DisplayName("상품 스냅샷 생성 시, ")
    @Nested
    inner class Create {
        @DisplayName("상품 ID, 상품명, 주문 당시 가격이 유효하면 생성된다.")
        @Test
        fun create_whenAllFieldsAreValid() {
            // arrange
            val productId = 1L
            val productName = "Loopers T-Shirt"
            val productPrice = OrderItemPrice(10_000L)

            // act
            val snapshot = ProductSnapshot(
                productId = productId,
                productName = productName,
                productPrice = productPrice,
            )

            // assert
            assertAll(
                { assertThat(snapshot.productId).isEqualTo(productId) },
                { assertThat(snapshot.productName).isEqualTo(productName) },
                { assertThat(snapshot.productPrice).isEqualTo(productPrice) },
            )
        }

        @DisplayName("상품 ID가 1보다 작으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenProductIdIsLessThanOne() {
            // act & assert
            val result = assertThrows<CoreException> {
                ProductSnapshot(
                    productId = 0L,
                    productName = "Loopers T-Shirt",
                    productPrice = OrderItemPrice(10_000L),
                )
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("상품명이 비어있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenProductNameIsBlank() {
            // act & assert
            val result = assertThrows<CoreException> {
                ProductSnapshot(
                    productId = 1L,
                    productName = " ",
                    productPrice = OrderItemPrice(10_000L),
                )
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}
