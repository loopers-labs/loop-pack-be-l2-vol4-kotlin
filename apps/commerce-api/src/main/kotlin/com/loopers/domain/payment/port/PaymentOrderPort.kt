package com.loopers.domain.payment.port

import com.loopers.domain.order.model.OrderModel

interface PaymentOrderPort {
    fun getPayableOrder(userId: Long, orderId: Long): OrderModel
    fun getPendingOrder(orderId: Long): OrderModel
    fun markOrdered(orderId: Long)
    fun markPaymentFailed(orderId: Long)
    fun detachCoupon(orderId: Long)
}
