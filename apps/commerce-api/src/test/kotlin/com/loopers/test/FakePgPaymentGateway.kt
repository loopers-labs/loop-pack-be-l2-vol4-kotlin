package com.loopers.test

import com.loopers.domain.payment.PaymentGatewayPort
import com.loopers.domain.payment.PaymentGatewayRequest
import com.loopers.domain.payment.PaymentGatewayResponse
import com.loopers.domain.payment.PaymentStatus
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * PG 시뮬레이터를 기동하지 않고 E2E/통합 테스트를 격리하기 위한 Fake 게이트웨이.
 * 항상 PENDING 접수증(transactionKey)을 발급한다. (happy path)
 */
class FakePgPaymentGateway : PaymentGatewayPort {
    private val sequence = AtomicInteger(0)

    val invocations: MutableList<PaymentGatewayRequest> = CopyOnWriteArrayList()

    fun reset() {
        invocations.clear()
    }

    override fun requestPayment(request: PaymentGatewayRequest): PaymentGatewayResponse {
        invocations += request
        val transactionKey = "TEST-TX-%06d".format(sequence.incrementAndGet())
        return PaymentGatewayResponse(
            transactionKey = transactionKey,
            status = PaymentStatus.PENDING,
        )
    }
}
