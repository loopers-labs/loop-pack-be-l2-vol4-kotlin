package com.loopers.application.order

import com.loopers.application.coupon.CouponApplicationService
import com.loopers.application.stock.StockApplicationService
import com.loopers.domain.order.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OrderReleaseService(
    private val orderApplicationService: OrderApplicationService,
    private val couponApplicationService: CouponApplicationService,
    private val stockApplicationService: StockApplicationService,
) {
    @Transactional
    fun markPaymentFailed(orderId: Long): Order {
        val order = orderApplicationService.markPaymentFailed(orderId)
        restoreStock(order)
        cancelCouponUseIfNeeded(order.userId, order.userCouponId)
        return order
    }

    @Transactional
    fun cancelOrder(orderId: Long): Order {
        val order = orderApplicationService.cancelOrder(orderId)
        restoreStock(order)
        cancelCouponUseIfNeeded(order.userId, order.userCouponId)
        return order
    }

    private fun restoreStock(order: Order) {
        order.items.forEach {
            stockApplicationService.restore(
                productId = it.productId,
                amount = it.quantity.value,
            )
        }
    }

    private fun cancelCouponUseIfNeeded(userId: Long, userCouponId: Long?) {
        userCouponId?.let {
            couponApplicationService.cancelCouponUse(userId = userId, userCouponId = it)
        }
    }
}
