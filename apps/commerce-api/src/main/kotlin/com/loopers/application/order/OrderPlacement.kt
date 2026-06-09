package com.loopers.application.order

import com.loopers.domain.brand.BrandService
import com.loopers.domain.coupon.UserCouponService
import com.loopers.domain.order.AppliedCoupon
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderItem
import com.loopers.domain.order.OrderItems
import com.loopers.domain.order.OrderService
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.order.PaymentResult
import com.loopers.domain.product.ProductService
import com.loopers.domain.stock.StockService
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 주문 트랜잭션 경계 전담(application). 외부 결제 호출을 트랜잭션 밖으로 분리하기 위해
 * "주문 확정([place])" 과 "결제 결과 반영([finalize])" 을 각각 독립 트랜잭션으로 둔다.
 * 오케스트레이션(결제 호출 포함)은 [OrderApplicationServiceAdapter] 가 트랜잭션 없이 조율한다.
 */
@Component
class OrderPlacement(
    private val orderService: OrderService,
    private val productService: ProductService,
    private val brandService: BrandService,
    private val stockService: StockService,
    private val userService: UserService,
    private val userCouponService: UserCouponService,
) {
    /**
     * 쿠폰 검증·사용 + 재고 차감 + 주문 저장을 한 트랜잭션으로 처리하고 PAYMENT_PENDING 주문을 반환한다.
     * 하나라도 실패하면 전체 롤백된다(원자성).
     */
    @Transactional
    fun place(command: CreateOrderCommand): Order {
        if (command.items.isEmpty()) {
            throw CoreException(ErrorType.BAD_REQUEST, "주문 항목은 최소 1개여야 합니다.")
        }
        userService.getById(command.userId)

        val orderItems = buildOrderItems(command.items)
        val totalAmount = orderItems.sumOf { it.subtotal() }

        val appliedCoupon = command.couponId?.let { couponId ->
            val usage = userCouponService.useForOrder(
                couponId = couponId,
                userId = command.userId,
                orderAmount = totalAmount,
                now = LocalDateTime.now(),
            )
            AppliedCoupon(
                issuedCouponId = usage.usedCoupon.id,
                couponName = usage.template.name,
                couponType = usage.template.type.name,
                couponValue = usage.template.value,
                discountAmount = usage.discount,
            )
        }

        // 비관적 락 획득 순서를 productId 오름차순으로 고정해 다중 상품 주문 간 데드락을 방지한다.
        orderItems.sortedBy { it.productId }
            .forEach { stockService.decrease(productId = it.productId, quantity = it.quantity) }

        val created = orderService.save(
            Order.create(userId = command.userId, items = OrderItems(orderItems), appliedCoupon = appliedCoupon),
        )
        return orderService.save(created.updateStatus(OrderStatus.PAYMENT_PENDING))
    }

    /**
     * 결제 결과를 별도 트랜잭션으로 반영한다.
     * - 성공: PAYMENT_COMPLETED 전이.
     * - 실패: 재고 복원 + 쿠폰 AVAILABLE 복원 + 주문 CANCELLED(보상).
     */
    @Transactional
    fun finalize(orderId: Long, paymentResult: PaymentResult): Order {
        val pending = orderService.getById(orderId)
        if (paymentResult.success) {
            return orderService.save(pending.updateStatus(OrderStatus.PAYMENT_COMPLETED))
        }
        pending.items.list.forEach { stockService.restore(productId = it.productId, quantity = it.quantity) }
        pending.appliedCoupon?.let { userCouponService.restore(it.issuedCouponId) }
        return orderService.save(pending.cancel())
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
