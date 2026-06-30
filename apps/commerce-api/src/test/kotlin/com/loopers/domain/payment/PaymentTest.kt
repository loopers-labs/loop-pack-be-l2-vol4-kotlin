package com.loopers.domain.payment

import com.loopers.domain.order.model.Order
import com.loopers.domain.order.model.OrderItem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PaymentTest {
    @Test
    fun createsPendingPaymentFromPendingPaymentOrder() {
        val payment = Payment.pending(
            order = pendingOrder(),
            cardType = CardType.SAMSUNG,
            cardNo = "1234-5678-9012-3456",
            idempotencyKey = "payment-key",
            transactionKey = "20250816:TR:9577c5",
            reason = null,
        )

        assertThat(payment.status).isEqualTo(PaymentStatus.PENDING)
        assertThat(payment.amount).isEqualTo(10_000L)
        assertThat(payment.orderNumber).isNotBlank()
        assertThat(payment.transactionKey).isEqualTo("20250816:TR:9577c5")
    }

    @Test
    fun marksSyncRequiredPaymentPendingWithTransactionKey() {
        val payment = Payment.syncRequired(
            order = pendingOrder(),
            cardType = CardType.SAMSUNG,
            cardNo = "1234-5678-9012-3456",
            idempotencyKey = "payment-key",
            reason = "sync required",
        )

        payment.markPending(transactionKey = "20250816:TR:9577c5", reason = null)

        assertThat(payment.status).isEqualTo(PaymentStatus.PENDING)
        assertThat(payment.transactionKey).isEqualTo("20250816:TR:9577c5")
    }

    private fun pendingOrder(): Order {
        return Order.createPendingPayment(
            id = 1L,
            memberId = 1L,
            items = listOf(
                OrderItem.snapshot(
                    productId = 1L,
                    productName = "hoodie",
                    brandName = "loopers",
                    unitPrice = 10_000L,
                    quantity = 1L,
                ),
            ),
        )
    }
}
