package com.loopers.domain.shopping

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CartItemTest {
    @DisplayName("CartItem quantity 는 1 이상이어야 한다.")
    @Test
    fun rejectsQuantityLessThanOne() {
        val exception = assertThrows<CoreException> {
            CartItem(cartId = 1L, productId = 10L, quantity = 0)
        }

        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }

    @DisplayName("increaseQuantity 는 기존 수량에 요청 수량을 더한다.")
    @Test
    fun increasesQuantity() {
        val item = CartItem(cartId = 1L, productId = 10L, quantity = 2)

        item.increaseQuantity(3)

        assertThat(item.quantity).isEqualTo(5)
    }
}
