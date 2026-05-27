package com.loopers.application.order.usecase

import com.loopers.application.order.OrderCommand
import com.loopers.application.order.OrderInfo
import com.loopers.domain.order.OrderDomainService
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductStockRepository
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CreateOrderUsecase(
    private val userService: UserService,
    private val productRepository: ProductRepository,
    private val productStockRepository: ProductStockRepository,
    private val orderRepository: OrderRepository,
) {
    private val orderDomainService = OrderDomainService()

    @Transactional
    fun execute(command: OrderCommand): OrderInfo {
        val user = userService.getProfile(loginId = command.loginId, password = command.password)
        val orderProducts = command.items.map { item ->
            val product = productRepository.findActiveById(item.productId)
                ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.")
            val stock = productStockRepository.findByProductId(item.productId)
                ?: throw CoreException(ErrorType.NOT_FOUND, "상품 재고를 찾을 수 없습니다.")

            OrderDomainService.OrderProduct(
                product = product,
                stock = stock,
                quantity = item.quantity,
            )
        }

        return orderDomainService.create(userId = user.id, items = orderProducts)
            .let { orderRepository.save(it) }
            .let { OrderInfo.from(it) }
    }
}
