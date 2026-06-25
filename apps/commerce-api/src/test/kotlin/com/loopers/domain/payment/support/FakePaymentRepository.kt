package com.loopers.domain.payment.support

import com.loopers.domain.payment.model.PaymentModel
import com.loopers.domain.payment.port.PaymentRepository

class FakePaymentRepository : PaymentRepository {
    private val payments = linkedMapOf<Long, PaymentModel>()
    private var sequence = 1L
    val lockedOrderIds = mutableListOf<Long>()
    val lockedTransactionKeys = mutableListOf<String>()

    override fun save(payment: PaymentModel): PaymentModel {
        val saved = if (payment.id == 0L) payment.withId(sequence++) else payment
        payments[saved.id] = saved
        return saved
    }

    override fun findByIdOrNull(paymentId: Long): PaymentModel? = payments[paymentId]

    override fun findByOrderIdOrNull(orderId: Long): PaymentModel? =
        payments.values.firstOrNull { it.orderId == orderId }

    override fun findByOrderIdForUpdateOrNull(orderId: Long): PaymentModel? {
        lockedOrderIds.add(orderId)
        return findByOrderIdOrNull(orderId)
    }

    override fun findByExternalTransactionKeyOrNull(externalTransactionKey: String): PaymentModel? =
        payments.values.firstOrNull { it.externalTransactionKey == externalTransactionKey }

    override fun findByExternalTransactionKeyForUpdateOrNull(externalTransactionKey: String): PaymentModel? {
        lockedTransactionKeys.add(externalTransactionKey)
        return findByExternalTransactionKeyOrNull(externalTransactionKey)
    }
}
