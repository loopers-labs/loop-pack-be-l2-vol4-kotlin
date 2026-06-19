package com.loopers.application.shopping

import com.loopers.application.order.OrderCheckoutFacade
import com.loopers.application.order.OrderInfo
import com.loopers.domain.order.OrderCommand
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CartCheckoutFacade(
    private val cartApplicationService: CartApplicationService,
    private val cartCatalogPort: CartCatalogPort,
    private val orderCheckoutFacade: OrderCheckoutFacade,
) {
    @Transactional
    fun checkout(command: CartCommand.Checkout): OrderInfo.Detail {
        val cartItems = cartApplicationService.getItems(command.userId)
        if (cartItems.isEmpty()) {
            throw CoreException(ErrorType.BAD_REQUEST, "쇼핑카트가 비어 있습니다.")
        }

        val products = cartCatalogPort.getCartProducts(cartItems.map { it.productId }).associateBy { it.productId }
        val orderItems = cartItems.map { item ->
            val product = products[item.productId]
                ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.")
            if (!product.orderable) {
                throw CoreException(ErrorType.BAD_REQUEST, "주문할 수 없는 상품입니다.")
            }
            OrderCommand.CheckoutItem(
                productId = item.productId,
                productNameSnapshot = product.productName,
                brandNameSnapshot = product.brandName,
                priceSnapshot = product.price,
                quantity = item.quantity,
            )
        }

        val order = orderCheckoutFacade.checkout(
            OrderCommand.Checkout(
                userId = command.userId,
                items = orderItems,
                deliveryAddress = command.deliveryAddress,
                deliveryRequest = command.deliveryRequest,
                phoneNumber = command.phoneNumber,
                reservationExpiresAt = command.reservationExpiresAt,
            ),
        )
        cartApplicationService.clear(command.userId)
        return order
    }
}
