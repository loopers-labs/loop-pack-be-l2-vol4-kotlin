package com.loopers.domain.order.application

import com.loopers.domain.order.application.command.OrderCreateCommand
import com.loopers.domain.product.application.service.ProductService
import org.springframework.stereotype.Component

@Component
class OrderQueueGatePolicy(
    private val productService: ProductService,
) {
    fun requiresAdmission(command: OrderCreateCommand): Boolean =
        productService.requiresWaitingQueue(command.items.map { it.productId })
}
