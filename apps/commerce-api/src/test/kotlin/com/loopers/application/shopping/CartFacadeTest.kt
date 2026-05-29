package com.loopers.application.shopping

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

class CartFacadeTest {
    private lateinit var cartRepository: FakeCartRepository
    private lateinit var catalogPort: FakeCartCatalogPort
    private lateinit var cartApplicationService: CartApplicationService
    private lateinit var cartFacade: CartFacade

    @BeforeEach
    fun setUp() {
        cartRepository = FakeCartRepository()
        catalogPort = FakeCartCatalogPort()
        cartApplicationService = CartApplicationService(cartRepository)
        cartFacade = CartFacade(cartApplicationService, catalogPort)
    }

    @DisplayName("addItem 을 호출할 때,")
    @Nested
    inner class AddItem {
        @DisplayName("새 상품을 쇼핑카트에 담는다.")
        @Test
        fun addsNewItem() {
            catalogPort.register(productId = 100L, stockQuantity = 10)

            cartFacade.addItem(CartCommand.AddItem(userId = 1L, productId = 100L, quantity = 2))

            val items = cartApplicationService.getItems(userId = 1L)
            assertAll(
                { assertThat(items).hasSize(1) },
                { assertThat(items[0].productId).isEqualTo(100L) },
                { assertThat(items[0].quantity).isEqualTo(2) },
            )
        }

        @DisplayName("이미 담긴 상품이면 기존 수량을 증가시킨다.")
        @Test
        fun incrementsExistingItem() {
            catalogPort.register(productId = 100L, stockQuantity = 10)
            cartFacade.addItem(CartCommand.AddItem(userId = 1L, productId = 100L, quantity = 2))

            cartFacade.addItem(CartCommand.AddItem(userId = 1L, productId = 100L, quantity = 3))

            val item = cartApplicationService.getItems(userId = 1L).single()
            assertThat(item.quantity).isEqualTo(5)
        }

        @DisplayName("요청 수량이 1 미만이면 BAD_REQUEST 예외를 던진다.")
        @Test
        fun rejectsInvalidQuantity() {
            catalogPort.register(productId = 100L, stockQuantity = 10)

            val exception = assertThrows<CoreException> {
                cartFacade.addItem(CartCommand.AddItem(userId = 1L, productId = 100L, quantity = 0))
            }

            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("요청 후 수량이 현재 재고보다 크면 BAD_REQUEST 예외를 던진다.")
        @Test
        fun rejectsQuantityOverCurrentStock() {
            catalogPort.register(productId = 100L, stockQuantity = 4)
            cartFacade.addItem(CartCommand.AddItem(userId = 1L, productId = 100L, quantity = 3))

            val exception = assertThrows<CoreException> {
                cartFacade.addItem(CartCommand.AddItem(userId = 1L, productId = 100L, quantity = 2))
            }

            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("changeQuantity/removeItem/clear 를 호출할 때,")
    @Nested
    inner class Mutate {
        @DisplayName("수량을 현재 재고 이하의 값으로 변경한다.")
        @Test
        fun changesQuantity() {
            catalogPort.register(productId = 100L, stockQuantity = 10)
            cartFacade.addItem(CartCommand.AddItem(userId = 1L, productId = 100L, quantity = 2))

            cartFacade.changeQuantity(CartCommand.ChangeQuantity(userId = 1L, productId = 100L, quantity = 7))

            assertThat(cartApplicationService.getItems(userId = 1L).single().quantity).isEqualTo(7)
        }

        @DisplayName("상품 한 줄을 제거한다.")
        @Test
        fun removesItem() {
            catalogPort.register(productId = 100L, stockQuantity = 10)
            cartFacade.addItem(CartCommand.AddItem(userId = 1L, productId = 100L, quantity = 2))

            cartFacade.removeItem(CartCommand.RemoveItem(userId = 1L, productId = 100L))

            assertThat(cartApplicationService.getItems(userId = 1L)).isEmpty()
        }

        @DisplayName("쇼핑카트를 비운다.")
        @Test
        fun clearsCart() {
            catalogPort.register(productId = 100L, stockQuantity = 10)
            catalogPort.register(productId = 200L, stockQuantity = 10)
            cartFacade.addItem(CartCommand.AddItem(userId = 1L, productId = 100L, quantity = 2))
            cartFacade.addItem(CartCommand.AddItem(userId = 1L, productId = 200L, quantity = 1))

            cartFacade.clear(CartCommand.Clear(userId = 1L))

            assertThat(cartApplicationService.getItems(userId = 1L)).isEmpty()
        }
    }
}
