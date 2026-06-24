package com.loopers.infrastructure.payment.entity

import com.loopers.domain.BaseEntity
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(
    name = "order_payment",
    indexes = [
        Index(name = "idx_order_payment_order", columnList = "order_id, id"),
        Index(name = "idx_order_payment_member", columnList = "member_id, id"),
        Index(name = "idx_order_payment_transaction", columnList = "transaction_key"),
    ],
)
class PaymentEntity(
    @Column(name = "order_id", nullable = false)
    var orderId: Long,

    @Column(name = "order_number", nullable = false)
    var orderNumber: String,

    @Column(name = "member_id", nullable = false)
    var memberId: Long,

    @Column(name = "amount", nullable = false)
    var amount: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false)
    var cardType: CardType,

    @Column(name = "card_no", nullable = false)
    var cardNo: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: PaymentStatus,

    @Column(name = "transaction_key")
    var transactionKey: String?,

    @Column(name = "reason")
    var reason: String?,
) : BaseEntity() {
    fun update(payment: Payment) {
        orderId = payment.orderId
        orderNumber = payment.orderNumber
        memberId = payment.memberId
        amount = payment.amount
        cardType = payment.cardType
        cardNo = payment.cardNo
        status = payment.status
        transactionKey = payment.transactionKey
        reason = payment.reason
    }
}
