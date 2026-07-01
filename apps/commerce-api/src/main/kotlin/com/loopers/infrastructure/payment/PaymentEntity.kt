package com.loopers.infrastructure.payment

import com.loopers.domain.payment.Payment
import com.loopers.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(
    name = "payments",
    indexes = [
        Index(name = "idx_payments_transaction_key", columnList = "transaction_key", unique = true),
        Index(name = "idx_payments_order_id_created_at", columnList = "order_id, created_at"),
    ],
)
class PaymentEntity(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "order_id", nullable = false)
    val orderId: Long,

    @Column(name = "transaction_key", nullable = false, length = 64)
    val transactionKey: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false, length = 16)
    val cardType: CardType,

    @Column(name = "card_no", nullable = false, length = 32)
    val cardNo: String,

    @Column(name = "amount", nullable = false)
    val amount: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: PaymentStatusType,

    @Column(name = "reason")
    var reason: String?,
) : BaseEntity() {

    fun updateResult(status: PaymentStatusType, reason: String?) {
        this.status = status
        this.reason = reason
    }

    fun toDomain(): Payment = Payment(
        id = id,
        userId = userId,
        orderId = orderId,
        transactionKey = transactionKey,
        cardType = cardType.toDomain(),
        cardNo = cardNo,
        amount = amount,
        status = status.toDomain(),
        reason = reason,
    )

    companion object {
        fun from(domain: Payment): PaymentEntity = PaymentEntity(
            userId = domain.userId,
            orderId = domain.orderId,
            transactionKey = domain.transactionKey,
            cardType = CardType.from(domain.cardType),
            cardNo = domain.cardNo,
            amount = domain.amount,
            status = PaymentStatusType.from(domain.status),
            reason = domain.reason,
        )
    }
}
