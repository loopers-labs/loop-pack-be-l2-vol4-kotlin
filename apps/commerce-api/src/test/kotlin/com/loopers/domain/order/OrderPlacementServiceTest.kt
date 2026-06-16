package com.loopers.domain.order

import com.loopers.domain.inventory.model.Inventory
import com.loopers.domain.order.dto.OrderPlacementItem
import com.loopers.domain.order.service.OrderPlacementService
import com.loopers.fixture.product.ProductBrandFixture
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

class OrderPlacementServiceTest {
    private val orderPlacementService = OrderPlacementService()

    @DisplayName("주문 생성")
    @Nested
    inner class Place {
        @DisplayName("모든 상품 재고가 충분하면 재고를 차감하고 주문 스냅샷을 생성한다")
        @Test
        fun placesOrder() {
            val brand = ProductBrandFixture.createBrand(id = 1L, name = "loopers")
            val firstProduct = ProductBrandFixture.createProduct(id = 10L, brandId = brand.id, name = "hoodie", price = 10_000L)
            val secondProduct = ProductBrandFixture.createProduct(id = 20L, brandId = brand.id, name = "cap", price = 5_000L)
            val firstInventory = Inventory(productId = firstProduct.id, quantity = 10L)
            val secondInventory = Inventory(productId = secondProduct.id, quantity = 3L)

            val result = orderPlacementService.place(
                memberId = 100L,
                items = listOf(
                    OrderPlacementItem(productId = firstProduct.id, quantity = 2L),
                    OrderPlacementItem(productId = secondProduct.id, quantity = 1L),
                ),
                products = listOf(firstProduct, secondProduct),
                brands = listOf(brand),
                inventories = listOf(firstInventory, secondInventory),
            )

            assertAll(
                { assertThat(result.order.memberId).isEqualTo(100L) },
                { assertThat(result.order.status).isEqualTo(OrderStatus.COMPLETED) },
                { assertThat(result.order.totalAmount).isEqualTo(25_000L) },
                { assertThat(result.order.items).hasSize(2) },
                { assertThat(result.order.items.first().productName).isEqualTo("hoodie") },
                { assertThat(result.order.items.first().brandName).isEqualTo("loopers") },
                { assertThat(firstInventory.quantity).isEqualTo(8L) },
                { assertThat(secondInventory.quantity).isEqualTo(2L) },
            )
        }

        @DisplayName("동일 상품이 여러 번 요청되면 수량을 합산해 재고를 차감한다")
        @Test
        fun mergesDuplicatedProductItems() {
            val brand = ProductBrandFixture.createBrand(id = 1L, name = "loopers")
            val product = ProductBrandFixture.createProduct(id = 10L, brandId = brand.id, price = 10_000L)
            val inventory = Inventory(productId = product.id, quantity = 10L)

            val result = orderPlacementService.place(
                memberId = 100L,
                items = listOf(
                    OrderPlacementItem(productId = product.id, quantity = 2L),
                    OrderPlacementItem(productId = product.id, quantity = 3L),
                ),
                products = listOf(product),
                brands = listOf(brand),
                inventories = listOf(inventory),
            )

            assertAll(
                { assertThat(result.order.items).hasSize(1) },
                { assertThat(result.order.items.single().quantity).isEqualTo(5L) },
                { assertThat(result.order.totalAmount).isEqualTo(50_000L) },
                { assertThat(inventory.quantity).isEqualTo(5L) },
            )
        }

        @DisplayName("재고가 부족하면 주문을 생성할 수 없다")
        @Test
        fun throwsConflict_whenInventoryIsInsufficient() {
            val brand = ProductBrandFixture.createBrand(id = 1L)
            val product = ProductBrandFixture.createProduct(id = 10L, brandId = brand.id)
            val inventory = Inventory(productId = product.id, quantity = 1L)

            val result = assertThrows<CoreException> {
                orderPlacementService.place(
                    memberId = 100L,
                    items = listOf(OrderPlacementItem(productId = product.id, quantity = 2L)),
                    products = listOf(product),
                    brands = listOf(brand),
                    inventories = listOf(inventory),
                )
            }

            assertAll(
                { assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT) },
                { assertThat(inventory.quantity).isEqualTo(1L) },
            )
        }

        @DisplayName("상품이 없으면 주문을 생성할 수 없다")
        @Test
        fun throwsNotFound_whenProductDoesNotExist() {
            val result = assertThrows<CoreException> {
                orderPlacementService.place(
                    memberId = 100L,
                    items = listOf(OrderPlacementItem(productId = 10L, quantity = 1L)),
                    products = emptyList(),
                    brands = emptyList(),
                    inventories = emptyList(),
                )
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }
}
