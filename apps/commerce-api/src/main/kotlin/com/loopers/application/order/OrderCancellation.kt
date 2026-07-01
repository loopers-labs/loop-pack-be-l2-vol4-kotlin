package com.loopers.application.order

import com.loopers.domain.coupon.UserCouponService
import com.loopers.domain.order.OrderService
import com.loopers.domain.stock.StockService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 주문 취소 보상 트랜잭션을 전담한다([OrderPlacement] 의 대칭).
 * 결제 실패 등으로 주문을 취소할 때 재고·쿠폰을 원복하고 주문을 CANCELLED 로 전이한다.
 * 호출자(콜백 핸들러)의 트랜잭션에 합류해 원자적으로 처리된다.
 */
@Component
class OrderCancellation(
    private val orderService: OrderService,
    private val stockService: StockService,
    private val userCouponService: UserCouponService,
) {
    @Transactional
    fun cancel(orderId: Long) {
        val order = orderService.getById(orderId)
        order.items.list.forEach { stockService.restore(it.productId, it.quantity) }
        order.appliedCoupon?.let { userCouponService.restore(it.issuedCouponId) }
        orderService.save(order.cancel())
    }
}
