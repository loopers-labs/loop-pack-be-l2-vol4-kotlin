package com.loopers.domain.order.application

import com.loopers.domain.coupon.application.service.CouponService
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
import java.time.LocalDate
import java.time.ZoneId

@Component
class OrderFacade(
    private val userService: UserService,
    private val productService: ProductService,
    private val stockService: StockService,
    private val orderService: OrderService,
    private val couponService: CouponService,
) {
    @Transactional
    fun placeOrder(command: OrderCreateCommand): OrderInfo {
        userService.getById(command.userId)
        val idempotencyKey = command.idempotencyKey?.takeIf { it.isNotBlank() }
        idempotencyKey
            ?.let { orderService.findByIdempotencyKeyOrNull(it) }
            ?.let { return OrderInfo.from(it) }

        val orderItems = aggregateItems(command.items)
        val snapshots = productService.getOrderableSnapshots(orderItems.map { it.productId })
        val items = createOrderItems(orderItems, snapshots)
        val totalPrice = Money.of(items.sumOf { it.linePrice.value })
        val discountPrice = command.issuedCouponId
            ?.let { couponService.validateAndCalculateDiscount(command.userId, it, totalPrice) }
            ?: Money.of(0)

        stockService.decreaseAll(orderItems.map { StockDecreaseCommand(it.productId, it.quantity) })
        command.issuedCouponId?.let { couponService.useIssuedCoupon(it) }
        val order = orderService.placeOrder(
            orderedUserId = command.userId,
            items = items,
            idempotencyKey = idempotencyKey,
            issuedCouponId = command.issuedCouponId,
            discountPrice = discountPrice,
        )
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

    @Transactional(readOnly = true)
    fun findMyOrders(
        userId: Long,
        startAt: LocalDate?,
        endAt: LocalDate?,
    ): List<OrderInfo> {
        userService.getById(userId)
        return orderService.findByOrderedUserId(
            orderedUserId = userId,
            startAt = startAt?.atStartOfDay(DEFAULT_ZONE),
            endAt = endAt?.plusDays(1)?.atStartOfDay(DEFAULT_ZONE),
        ).map { OrderInfo.from(it) }
    }

    @Transactional(readOnly = true)
    fun findMyOrder(userId: Long, orderId: Long): OrderInfo {
        userService.getById(userId)
        val order = orderService.getById(orderId)
        if (!order.belongsTo(userId)) {
            throw CoreException(ErrorType.NOT_FOUND)
        }
        return OrderInfo.from(order)
    }

    @Transactional(readOnly = true)
    fun findAdminOrders(page: Int, size: Int): List<OrderInfo> =
        orderService.findAll(page, size).map { OrderInfo.from(it) }

    @Transactional(readOnly = true)
    fun findAdminOrder(orderId: Long): OrderInfo =
        OrderInfo.from(orderService.getById(orderId))

    companion object {
        private val DEFAULT_ZONE: ZoneId = ZoneId.systemDefault()
    }
}
