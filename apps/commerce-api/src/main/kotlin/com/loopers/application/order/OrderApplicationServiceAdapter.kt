package com.loopers.application.order

import com.loopers.domain.auth.AuthService
import com.loopers.domain.brand.BrandService
import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult
import com.loopers.domain.order.AdminOrderDetail
import com.loopers.domain.order.AdminOrderSummary
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderDetail
import com.loopers.domain.order.OrderItem
import com.loopers.domain.order.OrderItems
import com.loopers.domain.order.OrderService
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.order.OrderSummary
import com.loopers.domain.order.PaymentGateway
import com.loopers.domain.product.ProductService
import com.loopers.domain.stock.StockService
import com.loopers.domain.user.UserService
import com.loopers.interfaces.api.order.OrderAdminApplicationServicePort
import com.loopers.interfaces.api.order.OrderApplicationServicePort
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

@Service
class OrderApplicationServiceAdapter(
    private val orderService: OrderService,
    private val productService: ProductService,
    private val brandService: BrandService,
    private val stockService: StockService,
    private val userService: UserService,
    private val authService: AuthService,
    private val paymentGateway: PaymentGateway,
) : OrderApplicationServicePort,
    OrderAdminApplicationServicePort {

    @Transactional
    override fun createOrder(command: CreateOrderCommand): OrderDetail {
        if (command.items.isEmpty()) {
            throw CoreException(ErrorType.BAD_REQUEST, "주문 항목은 최소 1개여야 합니다.")
        }
        userService.getById(command.userId)

        val orderItems = buildOrderItems(command.items)

        orderItems.forEach { stockService.decrease(productId = it.productId, quantity = it.quantity) }

        val created = orderService.save(Order.create(userId = command.userId, items = OrderItems(orderItems)))
        val pending = orderService.save(created.updateStatus(OrderStatus.PAYMENT_PENDING))

        val paymentResult = paymentGateway.requestPayment(orderId = pending.id, amount = pending.getActualAmount())

        val finalized = if (paymentResult.success) {
            orderService.save(pending.updateStatus(OrderStatus.PAYMENT_COMPLETED))
        } else {
            orderItems.forEach { stockService.restore(productId = it.productId, quantity = it.quantity) }
            orderService.save(pending.cancel())
        }

        return OrderDetail.from(finalized)
    }

    @Transactional(readOnly = true)
    override fun getMyOrders(
        userId: Long,
        startAt: ZonedDateTime,
        endAt: ZonedDateTime,
        pageRequest: PageRequest,
    ): PageResult<OrderSummary> {
        if (startAt.isAfter(endAt)) {
            throw CoreException(ErrorType.BAD_REQUEST, "startAt 은 endAt 이후일 수 없습니다.")
        }
        userService.getById(userId)
        val page = orderService.findAllByUserId(userId, startAt, endAt, pageRequest)
        return PageResult.of(
            items = page.items.map { OrderSummary.from(it) },
            pageRequest = pageRequest,
            totalElements = page.totalElements,
        )
    }

    @Transactional(readOnly = true)
    override fun getMyOrder(userId: Long, orderId: Long): OrderDetail {
        val order = orderService.getById(orderId)
        if (order.userId != userId) {
            throw CoreException(ErrorType.FORBIDDEN, "본인의 주문만 조회할 수 있습니다.")
        }
        return OrderDetail.from(order)
    }

    @Transactional(readOnly = true)
    override fun getOrders(pageRequest: PageRequest): PageResult<AdminOrderSummary> {
        val page = orderService.findAll(pageRequest)
        val userIds = page.items.map { it.userId }.distinct()
        val loginIds = authService.findLoginIdsByUserIds(userIds)
        val summaries = page.items.map { order ->
            AdminOrderSummary.of(order, loginIds[order.userId].orEmpty())
        }
        return PageResult.of(items = summaries, pageRequest = pageRequest, totalElements = page.totalElements)
    }

    @Transactional(readOnly = true)
    override fun getOrder(orderId: Long): AdminOrderDetail {
        val order = orderService.getById(orderId)
        val loginId = authService.findLoginIdsByUserIds(listOf(order.userId))[order.userId].orEmpty()
        return AdminOrderDetail.of(order, loginId)
    }

    private fun buildOrderItems(items: List<CreateOrderItemCommand>): List<OrderItem> {
        val productIds = items.map { it.productId }
        val productsById = productService.findAllByIds(productIds).associateBy { it.id }
        productIds.forEach { pid ->
            productsById[pid] ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: $pid")
        }
        val brandIds = productsById.values.map { it.brandId }.distinct()
        val brandsById = brandService.findAllByIds(brandIds).associateBy { it.id }
        return items.map { ci ->
            val product = productsById.getValue(ci.productId)
            val brand = brandsById[product.brandId]
                ?: throw CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다: ${product.brandId}")
            OrderItem(
                productId = product.id,
                quantity = ci.quantity,
                snapshotProductName = product.name,
                snapshotPrice = product.price,
                snapshotBrandName = brand.name,
            )
        }
    }
}
