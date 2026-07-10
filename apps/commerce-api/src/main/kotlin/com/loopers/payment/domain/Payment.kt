package com.loopers.payment.domain

import com.loopers.domain.BaseEntity
import com.loopers.shared.domain.Money
import com.loopers.support.error.ConflictException
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "payment")
class Payment private constructor(
    orderId: Long,
    userId: Long,
    amount: Money,
    cardType: CardType,
) : BaseEntity() {
    @Column(name = "order_id", nullable = false, updatable = false)
    val orderId: Long = orderId

    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: Long = userId

    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "amount", nullable = false, updatable = false))
    val amount: Money = amount

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false, length = 20, updatable = false)
    val cardType: CardType = cardType

    @Column(name = "transaction_key", length = 100)
    var transactionKey: String? = null
        private set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: PaymentStatus = PaymentStatus.PENDING
        private set

    @Column(name = "reason")
    var reason: String? = null
        private set

    fun accept(transactionKey: String) {
        this.transactionKey = transactionKey
    }

    fun success() = transitionTo(PaymentStatus.SUCCESS)

    fun fail(reason: String?) {
        this.reason = reason
        transitionTo(PaymentStatus.FAILED)
    }

    fun markUnknown() = transitionTo(PaymentStatus.UNKNOWN)

    private fun transitionTo(target: PaymentStatus) {
        if (!status.canTransitionTo(target)) {
            throw ConflictException(PaymentErrorCode.INVALID_STATUS_TRANSITION)
        }
        status = target
    }

    companion object {
        fun create(orderId: Long, userId: Long, amount: Money, cardType: CardType): Payment =
            Payment(orderId, userId, amount, cardType)
    }
}

enum class PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED,
    UNKNOWN,
    ;

    fun canTransitionTo(target: PaymentStatus): Boolean = when (this) {
        PENDING -> target == SUCCESS || target == FAILED || target == UNKNOWN
        UNKNOWN -> target == SUCCESS || target == FAILED
        SUCCESS, FAILED -> false
    }
}

enum class CardType {
    SAMSUNG,
    KB,
    HYUNDAI,
}
