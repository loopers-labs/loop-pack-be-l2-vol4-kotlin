package com.loopers.domain.payment.infrastructure

import com.loopers.domain.order.application.service.OrderService
import com.loopers.domain.order.model.OrderModel
import com.loopers.domain.order.model.OrderStatus
import com.loopers.domain.payment.constant.PaymentErrorMessages
import com.loopers.domain.payment.port.PaymentOrderPort
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class PaymentOrderAdapter(
    private val orderService: OrderService,
) : PaymentOrderPort {
    override fun getPayableOrder(userId: Long, orderId: Long): OrderModel {
        val order = orderService.getById(orderId)
        if (!order.belongsTo(userId)) {
            throw CoreException(ErrorType.NOT_FOUND)
        }
        return requirePending(order)
    }

    override fun getPendingOrder(orderId: Long): OrderModel =
        requirePending(orderService.getById(orderId))

    private fun requirePending(order: OrderModel): OrderModel {
        if (order.status != OrderStatus.PAYMENT_PENDING) {
            throw CoreException(ErrorType.CONFLICT, PaymentErrorMessages.ORDER_NOT_PAYABLE)
        }
        return order
    }

    override fun markOrdered(orderId: Long) {
        orderService.markOrdered(orderId)
    }

    override fun markPaymentFailed(orderId: Long) {
        orderService.markPaymentFailed(orderId)
    }

    override fun detachCoupon(orderId: Long) {
        orderService.detachCoupon(orderId)
    }
}
