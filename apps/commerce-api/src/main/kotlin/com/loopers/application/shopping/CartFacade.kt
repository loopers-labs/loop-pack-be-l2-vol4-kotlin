package com.loopers.application.shopping

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CartFacade(
    private val cartApplicationService: CartApplicationService,
    private val cartCatalogPort: CartCatalogPort,
) {
    @Transactional
    fun addItem(command: CartCommand.AddItem) {
        val product = getOrderableProduct(command.productId)
        cartApplicationService.addItem(command.userId, command.productId, command.quantity, product.stockQuantity)
    }

    @Transactional
    fun changeQuantity(command: CartCommand.ChangeQuantity) {
        val product = getOrderableProduct(command.productId)
        cartApplicationService.changeQuantity(command.userId, command.productId, command.quantity, product.stockQuantity)
    }

    @Transactional
    fun removeItem(command: CartCommand.RemoveItem) {
        cartApplicationService.removeItem(command.userId, command.productId)
    }

    @Transactional
    fun clear(command: CartCommand.Clear) {
        cartApplicationService.clear(command.userId)
    }

    private fun getOrderableProduct(productId: Long): CartProductInfo {
        val product = cartCatalogPort.getCartProduct(productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.")
        if (!product.orderable) {
            throw CoreException(ErrorType.BAD_REQUEST, "주문할 수 없는 상품입니다.")
        }
        return product
    }
}
