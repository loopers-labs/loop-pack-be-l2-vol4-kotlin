package com.loopers.domain.payment

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "payment_events")
class PaymentEvent(
    @Column(name = "order_id", nullable = false)
    val orderId: Long,

    @Column(name = "payment_id")
    val paymentId: Long?,

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    val eventType: PaymentEventType,

    @Enumerated(EnumType.STRING)
    @Column(name = "pg_provider", nullable = false, length = 30)
    val pgProvider: PgProvider,

    @Column(name = "payment_request_id", nullable = false, length = 100)
    val paymentRequestId: String,

    @Column(name = "payment_key", length = 100)
    val paymentKey: String?,

    @Column(name = "pg_transaction_id", length = 100)
    val pgTransactionId: String?,

    @Column(name = "requested_amount", nullable = false)
    val requestedAmount: Long,

    @Column(name = "approved_amount")
    val approvedAmount: Long?,

    @Column(name = "pg_status", length = 50)
    val pgStatus: String?,

    @Column(name = "failure_reason", length = 500)
    val failureReason: String?,

    @Column(name = "raw_response_summary", length = 1000)
    val rawResponseSummary: String?,
) : BaseEntity()
