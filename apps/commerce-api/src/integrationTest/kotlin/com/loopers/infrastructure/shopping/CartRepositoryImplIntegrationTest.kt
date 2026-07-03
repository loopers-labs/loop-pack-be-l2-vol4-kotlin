package com.loopers.infrastructure.shopping

import com.loopers.domain.shopping.Cart
import com.loopers.domain.shopping.CartItem
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class CartRepositoryImplIntegrationTest @Autowired constructor(
    private val cartRepositoryImpl: CartRepositoryImpl,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("사용자별 Cart 와 CartItem 을 저장하고 조회한다.")
    @Test
    fun savesAndFindsCartItems() {
        val cart = cartRepositoryImpl.save(Cart(userId = 1L))
        cartRepositoryImpl.saveItem(CartItem(cartId = cart.id, productId = 100L, quantity = 2))

        val foundCart = cartRepositoryImpl.findByUserId(1L)
        val items = cartRepositoryImpl.findItems(cart.id)

        assertAll(
            { assertThat(foundCart?.id).isEqualTo(cart.id) },
            { assertThat(items).hasSize(1) },
            { assertThat(items[0].productId).isEqualTo(100L) },
            { assertThat(items[0].quantity).isEqualTo(2) },
        )
    }

    @DisplayName("deleteItemsByCartId 는 해당 Cart 의 품목만 제거한다.")
    @Test
    fun deletesItemsByCartId() {
        val cart = cartRepositoryImpl.save(Cart(userId = 1L))
        val otherCart = cartRepositoryImpl.save(Cart(userId = 2L))
        cartRepositoryImpl.saveItem(CartItem(cartId = cart.id, productId = 100L, quantity = 2))
        cartRepositoryImpl.saveItem(CartItem(cartId = otherCart.id, productId = 200L, quantity = 1))

        cartRepositoryImpl.deleteItemsByCartId(cart.id)

        assertAll(
            { assertThat(cartRepositoryImpl.findItems(cart.id)).isEmpty() },
            { assertThat(cartRepositoryImpl.findItems(otherCart.id)).hasSize(1) },
        )
    }
}
