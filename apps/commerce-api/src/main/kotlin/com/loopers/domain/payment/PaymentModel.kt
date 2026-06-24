package com.loopers.domain.payment

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "payments")
class PaymentModel(
    orderId: Long,
    userId: Long,
    amount: BigDecimal,
    cardType: CardType,
    cardNo: String,
) : BaseEntity() {
    @Column(name = "order_id", nullable = false)
    var orderId: Long = orderId
        protected set

    @Column(name = "user_id", nullable = false)
    var userId: Long = userId
        protected set

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    var amount: BigDecimal = amount
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false, length = 20)
    var cardType: CardType = cardType
        protected set

    @Column(name = "card_no", nullable = false, length = 30)
    var maskedCardNo: String = mask(cardNo)
        protected set

    @Column(name = "transaction_key", unique = true, length = 64)
    var transactionKey: String? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: PaymentStatus = PaymentStatus.PENDING
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 30)
    var failureReason: PaymentFailureReason? = null
        protected set

    init {
        if (orderId <= 0) throw CoreException(ErrorType.BAD_REQUEST, "주문 ID는 양수여야 합니다.")
        if (userId <= 0) throw CoreException(ErrorType.BAD_REQUEST, "회원 ID는 양수여야 합니다.")
        if (amount <= BigDecimal.ZERO) throw CoreException(ErrorType.BAD_REQUEST, "결제 금액은 양수여야 합니다.")
    }

    fun assignTransactionKey(key: String) {
        this.transactionKey = key
    }

    fun markSuccess() {
        if (status != PaymentStatus.PENDING) throw CoreException(ErrorType.CONFLICT, "확정할 수 없는 결제 상태입니다.")
        status = PaymentStatus.SUCCESS
    }

    fun markFailed(reason: PaymentFailureReason) {
        if (status != PaymentStatus.PENDING) throw CoreException(ErrorType.CONFLICT, "실패 처리할 수 없는 결제 상태입니다.")
        status = PaymentStatus.FAILED
        failureReason = reason
    }

    fun isPending(): Boolean = status == PaymentStatus.PENDING

    companion object {
        // ponytail: 마지막 4자리만 노출. PAN 전체는 저장/로깅하지 않는다.
        private fun mask(cardNo: String): String {
            val digitsOnly = cardNo.filter { it.isDigit() }
            val last4 = digitsOnly.takeLast(4)
            return "****-****-****-$last4"
        }
    }
}
