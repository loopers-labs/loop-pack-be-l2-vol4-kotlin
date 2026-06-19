package com.loopers.application.shopping

import com.loopers.config.redis.InMemoryRedisTemplate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

class CartQueryFacadeTest {
    @DisplayName("쇼핑카트 조회는 CartItem 과 Catalog 현재 상품 정보를 조합한다.")
    @Test
    fun composesCartItemsWithCurrentCatalogInfo() {
        val cartRepository = FakeCartRepository()
        val catalogPort = FakeCartCatalogPort()
        val cartApplicationService = CartApplicationService(cartRepository, InMemoryRedisTemplate())
        val cartFacade = CartFacade(cartApplicationService, catalogPort)
        val cartQueryFacade = CartQueryFacade(cartApplicationService, catalogPort)
        catalogPort.register(productId = 100L, productName = "현재 상품명", brandName = "현재 브랜드명", price = 12000L, stockQuantity = 8)
        cartFacade.addItem(CartCommand.AddItem(userId = 1L, productId = 100L, quantity = 2))

        val result = cartQueryFacade.getCart(userId = 1L)

        assertAll(
            { assertThat(result.userId).isEqualTo(1L) },
            { assertThat(result.items).hasSize(1) },
            { assertThat(result.items[0].productName).isEqualTo("현재 상품명") },
            { assertThat(result.items[0].brandName).isEqualTo("현재 브랜드명") },
            { assertThat(result.items[0].price).isEqualTo(12000L) },
            { assertThat(result.items[0].quantity).isEqualTo(2) },
            { assertThat(result.items[0].orderable).isTrue() },
            { assertThat(result.items[0].stockQuantity).isEqualTo(8) },
        )
    }

    @DisplayName("Catalog 에서 사라진 상품도 CartItem 은 유지하고 주문 불가로 표시한다.")
    @Test
    fun keepsMissingCatalogProductAsNotOrderableLine() {
        val cartRepository = FakeCartRepository()
        val catalogPort = FakeCartCatalogPort()
        val cartApplicationService = CartApplicationService(cartRepository, InMemoryRedisTemplate())
        val cartFacade = CartFacade(cartApplicationService, catalogPort)
        val cartQueryFacade = CartQueryFacade(cartApplicationService, catalogPort)
        catalogPort.register(productId = 100L, stockQuantity = 8)
        cartFacade.addItem(CartCommand.AddItem(userId = 1L, productId = 100L, quantity = 2))
        catalogPort.clear()

        val result = cartQueryFacade.getCart(userId = 1L)

        assertAll(
            { assertThat(result.items).hasSize(1) },
            { assertThat(result.items[0].productId).isEqualTo(100L) },
            { assertThat(result.items[0].productName).isNull() },
            { assertThat(result.items[0].orderable).isFalse() },
        )
    }
}
