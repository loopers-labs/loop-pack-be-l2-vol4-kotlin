package com.loopers.application.order

import com.loopers.domain.coupon.UserCouponService
import com.loopers.domain.order.OrderCreatedEvent
import com.loopers.domain.order.OrderItemModel
import com.loopers.domain.order.OrderService
import com.loopers.domain.product.ProductService
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

@Component
class OrderFacade(
    private val userService: UserService,
    private val productService: ProductService,
    private val orderService: OrderService,
    private val userCouponService: UserCouponService,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun place(
        loginId: String,
        rawPassword: String,
        requestItems: List<PlaceOrderLine>,
        couponId: Long? = null,
    ): OrderInfo {
        if (requestItems.isEmpty()) {
            throw CoreException(ErrorType.BAD_REQUEST, "주문 항목은 최소 1개 이상이어야 합니다.")
        }
        val duplicateIds = requestItems.groupingBy { it.productId }.eachCount().filterValues { it > 1 }.keys
        if (duplicateIds.isNotEmpty()) {
            throw CoreException(ErrorType.BAD_REQUEST, "동일한 상품을 여러 라인에 담을 수 없습니다: $duplicateIds")
        }

        val user = userService.authenticate(loginId, rawPassword)
        val productIds = requestItems.map { it.productId }
        val productsById = productService.findAllOnSaleByIds(productIds).associateBy { it.id }

        requestItems.forEach { req ->
            val product = productsById[req.productId]
                ?: throw CoreException(ErrorType.NOT_FOUND, "판매중인 상품이 아닙니다: ${req.productId}")
            if (!product.isOrderable(req.quantity)) {
                throw CoreException(ErrorType.CONFLICT, "주문 가능 상태가 아니거나 재고가 부족합니다: ${req.productId}")
            }
        }

        // 데드락 회피를 위해 productId 오름차순으로 재고를 원자적으로 차감한다.
        requestItems.sortedBy { it.productId }.forEach { req ->
            productService.deductStock(req.productId, req.quantity)
        }

        val orderItems = requestItems.map { req ->
            val product = productsById.getValue(req.productId)
            OrderItemModel(
                productId = product.id,
                productNameSnapshot = product.name,
                unitPriceSnapshot = product.price,
                quantity = req.quantity,
            )
        }

        val originalAmount = orderItems.sumOf { it.lineTotal }
        val discountAmount = couponId?.let {
            userCouponService.use(user.id, it, originalAmount, ZonedDateTime.now())
        } ?: 0L

        val order = orderService.createPending(user.id, orderItems, couponId, discountAmount)
        eventPublisher.publishEvent(OrderCreatedEvent.from(order))
        return OrderInfo.from(order)
    }

    @Transactional(readOnly = true)
    fun getMyOrder(loginId: String, rawPassword: String, orderId: Long): OrderInfo {
        val user = userService.authenticate(loginId, rawPassword)
        val order = orderService.getByIdAndUserId(orderId, user.id)
        return OrderInfo.from(order)
    }

    @Transactional(readOnly = true)
    fun getMyOrders(
        loginId: String,
        rawPassword: String,
        startAt: ZonedDateTime,
        endAt: ZonedDateTime,
        pageable: Pageable,
    ): Page<OrderInfo> {
        val user = userService.authenticate(loginId, rawPassword)
        return orderService.getByUserIdAndPeriod(user.id, startAt, endAt, pageable).map(OrderInfo::from)
    }

    data class PlaceOrderLine(val productId: Long, val quantity: Int)
}
