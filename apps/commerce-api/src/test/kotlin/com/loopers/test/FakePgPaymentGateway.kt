package com.loopers.test

import com.loopers.domain.payment.PaymentGatewayPort
import com.loopers.domain.payment.PaymentGatewayRequest
import com.loopers.domain.payment.PaymentGatewayResponse
import com.loopers.domain.payment.PaymentStatus
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * PG 시뮬레이터를 기동하지 않고 E2E/통합 테스트를 격리하기 위한 Fake 게이트웨이.
 * - requestPayment: 항상 PENDING 접수증(transactionKey)을 발급한다.
 * - getTransaction: [stubTransaction] 으로 미리 심어둔 상태를 반환한다(미설정 시 null = PG 에 아직 없음).
 */
class FakePgPaymentGateway : PaymentGatewayPort {
    private val sequence = AtomicInteger(0)
    private val transactions = ConcurrentHashMap<String, PaymentGatewayResponse>()

    val invocations: MutableList<PaymentGatewayRequest> = CopyOnWriteArrayList()

    fun reset() {
        invocations.clear()
        transactions.clear()
    }

    /** 복구(reconcile) 테스트에서 PG 가 반환할 거래 상태를 심는다. */
    fun stubTransaction(transactionKey: String, status: PaymentStatus, reason: String? = null) {
        transactions[transactionKey] = PaymentGatewayResponse(transactionKey, status, reason)
    }

    override fun requestPayment(request: PaymentGatewayRequest): PaymentGatewayResponse {
        invocations += request
        val transactionKey = "TEST-TX-%06d".format(sequence.incrementAndGet())
        return PaymentGatewayResponse(
            transactionKey = transactionKey,
            status = PaymentStatus.PENDING,
        )
    }

    override fun getTransaction(userId: Long, transactionKey: String): PaymentGatewayResponse? =
        transactions[transactionKey]
}
