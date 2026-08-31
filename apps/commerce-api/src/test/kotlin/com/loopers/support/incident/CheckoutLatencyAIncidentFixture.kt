package com.loopers.support.incident

import com.loopers.domain.payment.PaymentGateway
import java.util.LinkedHashMap

// Hides: deterministic COMMIT_PENDING_THEN_DROP ordinals and second-dispatch rejection.
class CheckoutLatencyAIncidentFixture : PaymentGateway {
    private val transactions = LinkedHashMap<String, PaymentGateway.Result>()
    var dispatches = 0
        private set
    private var faultEnabled = false
    fun enableFault() { faultEnabled = true }
    override fun create(userId: String, request: PaymentGateway.Request): PaymentGateway.Result {
        check(userId == "135135") { "unexpected X-USER-ID" }
        check(!transactions.containsKey(request.orderId)) { "REJECT_SECOND_DISPATCH" }
        dispatches++
        val result = PaymentGateway.Result("tx-%02d".format(dispatches), request.orderId, "CONFIRMED")
        transactions[request.orderId] = result
        if (faultEnabled && dispatches in 4..6) error("COMMIT_PENDING_THEN_DROP")
        return result
    }
    override fun findByOrderId(userId: String, providerOrderId: String): List<PaymentGateway.Result> =
        transactions[providerOrderId]?.let(::listOf) ?: emptyList()
    override fun findByTransactionKey(userId: String, key: String): PaymentGateway.Result =
        transactions.values.single { it.transactionKey == key }
    fun providerEffects() = transactions.size
    fun reset() { transactions.clear(); dispatches = 0; faultEnabled = false }
}
