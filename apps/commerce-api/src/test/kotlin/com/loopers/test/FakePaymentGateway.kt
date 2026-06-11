package com.loopers.test

import com.loopers.domain.order.PaymentGateway
import com.loopers.domain.order.PaymentResult
import java.util.concurrent.CopyOnWriteArrayList

class FakePaymentGateway : PaymentGateway {
    @Volatile
    var alwaysSucceeds: Boolean = true

    val invocations: MutableList<Invocation> = CopyOnWriteArrayList()

    fun reset() {
        alwaysSucceeds = true
        invocations.clear()
    }

    override fun requestPayment(orderId: Long, amount: Long): PaymentResult {
        invocations += Invocation(orderId = orderId, amount = amount)
        return if (alwaysSucceeds) {
            PaymentResult(success = true, message = null)
        } else {
            PaymentResult(success = false, message = "결제 실패")
        }
    }

    data class Invocation(val orderId: Long, val amount: Long)
}
