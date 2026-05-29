package com.loopers.application.order

import com.loopers.application.product.ProductApplicationService
import com.loopers.application.user.UserApplicationService
import com.loopers.domain.order.OrderItem
import com.loopers.domain.order.OrderItemPrice
import com.loopers.domain.order.OrderQuantity
import com.loopers.domain.order.ProductSnapshot
import com.loopers.domain.product.Product
import com.loopers.domain.product.StockQuantity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OrderFacade(
    private val orderApplicationService: OrderApplicationService,
    private val productApplicationService: ProductApplicationService,
    private val userApplicationService: UserApplicationService,
) {
    @Transactional
    fun createOrder(command: CreateOrderCommand): OrderInfo {
        userApplicationService.getUser(command.userId)

        val orderItems = command.items.map { itemCommand ->
            val product = productApplicationService.getProduct(itemCommand.productId)
            val quantity = OrderQuantity(itemCommand.quantity)

            productApplicationService.deductStock(
                id = product.idOrThrow(),
                quantity = StockQuantity(quantity.value),
            )

            product.toOrderItem(quantity)
        }

        return orderApplicationService.createOrder(
            userId = command.userId,
            items = orderItems,
        ).let { OrderInfo.from(it) }
    }

    private fun Product.toOrderItem(quantity: OrderQuantity): OrderItem {
        return OrderItem(
            productSnapshot = ProductSnapshot(
                productId = idOrThrow(),
                productName = name,
                productPrice = OrderItemPrice(price.amount),
            ),
            quantity = quantity,
        )
    }

    private fun Product.idOrThrow(): Long {
        return id ?: throw CoreException(ErrorType.INTERNAL_ERROR, "상품 ID가 존재하지 않습니다.")
    }
}
