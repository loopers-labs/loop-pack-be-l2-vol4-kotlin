package com.loopers.domain.order

import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductStockModel
import com.loopers.domain.product.ProductStockRepository
import com.loopers.domain.user.UserRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OrderService(
    private val orderRepository: OrderRepository,
    private val userRepository: UserRepository,
    private val productRepository: ProductRepository,
    private val productStockRepository: ProductStockRepository,
) {
    @Transactional
    fun order(command: OrderCommand): OrderModel {
        userRepository.findById(command.userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다.")
        if (command.items.isEmpty()) throw CoreException(ErrorType.BAD_REQUEST, "주문 항목은 비어있을 수 없습니다.")
        if (command.items.map { it.productId }.distinct().size != command.items.size) {
            throw CoreException(ErrorType.BAD_REQUEST, "하나의 주문에 같은 상품을 중복으로 담을 수 없습니다.")
        }

        val purchasableItems = command.items.map { item ->
            val product = productRepository.findActiveById(item.productId)
                ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.")
            val stock = productStockRepository.findByProductId(item.productId)
                ?: throw CoreException(ErrorType.NOT_FOUND, "상품 재고를 찾을 수 없습니다.")

            PurchasableItem(
                product = product,
                stock = stock,
                quantity = item.quantity,
            )
        }

        purchasableItems.forEach {
            if (!it.stock.hasEnough(it.quantity)) {
                throw CoreException(ErrorType.CONFLICT, "상품 재고가 부족합니다.")
            }
        }

        val orderItems = purchasableItems.map {
            it.stock.deduct(it.quantity)
            it.product.syncStock(it.stock.quantity)
            it.product.toOrderItem(it.quantity)
        }

        return orderRepository.save(
            OrderModel(
                userId = command.userId,
                items = orderItems,
            ),
        )
    }

    data class OrderCommand(
        val userId: Long,
        val items: List<OrderItemCommand>,
    )

    data class OrderItemCommand(
        val productId: Long,
        val quantity: Int,
    )

    private data class PurchasableItem(
        val product: ProductModel,
        val stock: ProductStockModel,
        val quantity: Int,
    )
}
