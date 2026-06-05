package com.loopers.domain.order.application

import com.loopers.domain.order.application.command.OrderCreateCommand
import com.loopers.domain.order.application.command.OrderItemCreateCommand
import com.loopers.domain.order.application.info.OrderInfo
import com.loopers.domain.order.application.service.OrderService
import com.loopers.domain.order.exception.OrderDomainException
import com.loopers.domain.order.model.OrderItemModel
import com.loopers.domain.product.application.command.StockDecreaseCommand
import com.loopers.domain.product.application.info.ProductSnapshotInfo
import com.loopers.domain.product.application.service.ProductService
import com.loopers.domain.product.application.service.StockService
import com.loopers.domain.product.exception.ProductDomainException
import com.loopers.domain.product.vo.Money
import com.loopers.domain.product.vo.Quantity
import com.loopers.domain.user.application.service.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OrderFacade(
    private val userService: UserService,
    private val productService: ProductService,
    private val stockService: StockService,
    private val orderService: OrderService,
) {
    @Transactional
    fun placeOrder(command: OrderCreateCommand): OrderInfo {
        userService.findById(command.userId)
        val idempotencyKey = command.idempotencyKey?.takeIf { it.isNotBlank() }
        idempotencyKey
            ?.let { orderService.findByIdempotencyKey(it) }
            ?.let { return OrderInfo.from(it) }

        val orderItems = aggregateItems(command.items)
        val snapshots = productService.findOrderableSnapshots(orderItems.map { it.productId })
        val items = createOrderItems(orderItems, snapshots)
        val order = orderService.placeOrder(command.userId, items, idempotencyKey)
        stockService.decreaseAll(orderItems.map { StockDecreaseCommand(it.productId, it.quantity) })
        return OrderInfo.from(order)
    }

    private fun aggregateItems(items: List<OrderItemCreateCommand>): List<OrderItemCreateCommand> =
        try {
            items
                .groupBy { it.productId }
                .map { (productId, itemsByProduct) ->
                    OrderItemCreateCommand(
                        productId = productId,
                        quantity = itemsByProduct.sumOf { Quantity.of(it.quantity).value },
                    )
                }
        } catch (e: ProductDomainException) {
            throw CoreException(ErrorType.BAD_REQUEST, e.message, e)
        }

    private fun createOrderItems(
        orderItems: List<OrderItemCreateCommand>,
        snapshots: List<ProductSnapshotInfo>,
    ): List<OrderItemModel> {
        val snapshotsByProductId = snapshots.associateBy { it.productId }
        return try {
            orderItems.map { item ->
                val snapshot = snapshotsByProductId[item.productId] ?: throw CoreException(ErrorType.NOT_FOUND)
                OrderItemModel.snapshotOf(
                    productId = item.productId,
                    quantity = Quantity.of(item.quantity),
                    snapshotProductName = snapshot.productName,
                    snapshotUnitPrice = Money.of(snapshot.unitPrice),
                )
            }
        } catch (e: OrderDomainException) {
            throw CoreException(ErrorType.BAD_REQUEST, e.message, e)
        } catch (e: ProductDomainException) {
            throw CoreException(ErrorType.BAD_REQUEST, e.message, e)
        }
    }
}
