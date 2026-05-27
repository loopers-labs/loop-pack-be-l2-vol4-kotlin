package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

class ProductModelTest {
    @DisplayName("상품을 생성할 때,")
    @Nested
    inner class Create {
        @DisplayName("브랜드, 가격, 재고가 유효하면 생성된다.")
        @Test
        fun createsProduct_whenFieldsAreValid() {
            // act
            val product = createProduct()

            // assert
            assertThat(product.stockQuantity).isEqualTo(10)
        }

        @DisplayName("재고가 음수이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenStockIsNegative() {
            // act
            val exception = assertThrows<CoreException> {
                createProduct(stockQuantity = -1)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("상품 재고를 차감할 때,")
    @Nested
    inner class DeductStock {
        @DisplayName("요청 수량만큼 재고를 차감한다.")
        @Test
        fun deductsStock_whenQuantityIsEnough() {
            // arrange
            val stock = ProductStockModel(productId = 1L, quantity = 10)

            // act
            stock.deduct(3)

            // assert
            assertThat(stock.quantity).isEqualTo(7)
        }

        @DisplayName("재고가 부족하면 CONFLICT 예외가 발생하고 재고는 음수가 되지 않는다.")
        @Test
        fun throwsConflict_whenQuantityIsNotEnough() {
            // arrange
            val stock = ProductStockModel(productId = 1L, quantity = 2)

            // act
            val exception = assertThrows<CoreException> {
                stock.deduct(3)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.CONFLICT)
            assertThat(stock.quantity).isEqualTo(2)
        }
    }

    private fun createProduct(stockQuantity: Int = 10): ProductModel {
        return ProductModel(
            brandId = 1L,
            name = "Air Max",
            description = "Shoes",
            price = BigDecimal("120000.00"),
            stockQuantity = stockQuantity,
        )
    }
}
