package com.loopers.domain.payment

import com.loopers.domain.order.model.Order
import com.loopers.domain.order.model.OrderItem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PaymentTest {
    @Test
    fun createsRequestingPaymentFromPendingPaymentOrder() {
        val payment = Payment.request(
            order = pendingOrder(),
            cardType = CardType.SAMSUNG,
            cardNo = "1234-5678-9012-3456",
        )

        assertThat(payment.status).isEqualTo(PaymentStatus.REQUESTING)
        assertThat(payment.amount).isEqualTo(10_000L)
    }

    @Test
    fun marksPaymentPendingWithTransactionKey() {
        val payment = Payment.request(
            order = pendingOrder(),
            cardType = CardType.SAMSUNG,
            cardNo = "1234-5678-9012-3456",
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
