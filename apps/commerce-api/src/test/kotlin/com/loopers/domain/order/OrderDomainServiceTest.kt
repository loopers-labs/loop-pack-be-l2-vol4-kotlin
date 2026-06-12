package com.loopers.domain.order

import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductStockModel
import com.loopers.domain.withId
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

class OrderDomainServiceTest {
    @DisplayName("주문을 생성할 때,")
    @Nested
    inner class Create {
        @DisplayName("여러 상품 주문을 생성하고 각 상품 재고를 차감한다.")
        @Test
        fun createsOrderAndDeductsStocks() {
            // arrange
            val service = OrderDomainService()
            val firstStock = ProductStockModel(productId = 10L, quantity = 10)
            val secondStock = ProductStockModel(productId = 20L, quantity = 5)

            // act
            val order = service.create(
                userId = 1L,
                items = listOf(
                    orderProduct(productId = 10L, price = "120000.00", stock = firstStock, quantity = 2),
                    orderProduct(productId = 20L, price = "30000.00", stock = secondStock, quantity = 1),
                ),
            )

            // assert
            assertAll(
                { assertThat(order.items).hasSize(2) },
                { assertThat(order.totalPrice).isEqualByComparingTo(BigDecimal("270000.00")) },
                { assertThat(firstStock.quantity).isEqualTo(8) },
                { assertThat(secondStock.quantity).isEqualTo(4) },
            )
        }

        @DisplayName("재고가 부족하면 CONFLICT 예외가 발생하고 어떤 재고도 차감하지 않는다.")
        @Test
        fun throwsConflictAndDoesNotDeductAnyStock_whenStockIsInsufficient() {
            // arrange
            val service = OrderDomainService()
            val firstStock = ProductStockModel(productId = 10L, quantity = 10)
            val secondStock = ProductStockModel(productId = 20L, quantity = 5)

            // act
            val exception = assertThrows<CoreException> {
                service.create(
                    userId = 1L,
                    items = listOf(
                        orderProduct(productId = 10L, price = "120000.00", stock = firstStock, quantity = 2),
                        orderProduct(productId = 20L, price = "30000.00", stock = secondStock, quantity = 99),
                    ),
                )
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.CONFLICT)
            assertThat(firstStock.quantity).isEqualTo(10)
            assertThat(secondStock.quantity).isEqualTo(5)
        }

        @DisplayName("같은 상품이 중복으로 포함되면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenSameProductIsDuplicated() {
            // arrange
            val service = OrderDomainService()
            val stock = ProductStockModel(productId = 10L, quantity = 10)

            // act
            val exception = assertThrows<CoreException> {
                service.create(
                    userId = 1L,
                    items = listOf(
                        orderProduct(productId = 10L, price = "120000.00", stock = stock, quantity = 1),
                        orderProduct(productId = 10L, price = "120000.00", stock = stock, quantity = 1),
                    ),
                )
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(stock.quantity).isEqualTo(10)
        }
    }

    private fun orderProduct(
        productId: Long,
        price: String,
        stock: ProductStockModel,
        quantity: Int,
    ): OrderDomainService.OrderProduct {
        return OrderDomainService.OrderProduct(
            product = ProductModel(
                brandId = 1L,
                name = "Product$productId",
                description = "Product",
                price = BigDecimal(price),
            ).withId(productId),
            stock = stock,
            quantity = quantity,
        )
    }
}
