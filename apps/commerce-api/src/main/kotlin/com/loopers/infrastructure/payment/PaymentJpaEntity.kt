package com.loopers.infrastructure.payment

import com.loopers.application.payment.PaymentStatus
import com.loopers.domain.BaseEntity
import com.loopers.domain.payment.Payment
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(
    name = "payments",
    indexes = [
        Index(name = "idx_payments_order_id", columnList = "order_id"),
        Index(name = "idx_payments_transaction_key", columnList = "transaction_key", unique = true),
    ],
)
class PaymentJpaEntity(
    orderId: Long,
    userId: Long,
    transactionKey: String,
    cardType: String,
    cardNo: String,
    amount: Long,
    status: PaymentStatus,
    reason: String?,
) : BaseEntity() {
    @Column(name = "order_id", nullable = false)
    val orderId: Long = orderId

    @Column(name = "user_id", nullable = false)
    val userId: Long = userId

    @Column(name = "transaction_key", nullable = false, length = 100)
    val transactionKey: String = transactionKey

    @Column(name = "card_type", nullable = false, length = 50)
    val cardType: String = cardType

    @Column(name = "card_no", nullable = false, length = 50)
    val cardNo: String = cardNo

    @Column(name = "amount", nullable = false)
    val amount: Long = amount

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    var status: PaymentStatus = status
        protected set

    @Column(name = "reason", length = 500)
    var reason: String? = reason
        protected set

    fun updateFrom(payment: Payment) {
        status = payment.status
        reason = payment.reason
    }

    fun toDomain(): Payment {
        return Payment(
            id = id,
            orderId = orderId,
            userId = userId,
            transactionKey = transactionKey,
            cardType = cardType,
            cardNo = cardNo,
            amount = amount,
            status = status,
            reason = reason,
        )
    }

    companion object {
        fun from(payment: Payment): PaymentJpaEntity {
            return PaymentJpaEntity(
                orderId = payment.orderId,
                userId = payment.userId,
                transactionKey = payment.transactionKey,
                cardType = payment.cardType,
                cardNo = payment.cardNo,
                amount = payment.amount,
                status = payment.status,
                reason = payment.reason,
            )
        }
    }
}
