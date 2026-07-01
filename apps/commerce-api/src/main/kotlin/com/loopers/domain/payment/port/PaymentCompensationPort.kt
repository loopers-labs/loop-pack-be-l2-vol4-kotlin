package com.loopers.domain.payment.port

import com.loopers.domain.order.model.OrderModel

interface PaymentCompensationPort {
    fun restore(order: OrderModel)
}
