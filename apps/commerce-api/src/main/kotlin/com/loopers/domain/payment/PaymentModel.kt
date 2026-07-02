package com.loopers.domain.payment

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "payments",
    uniqueConstraints = [UniqueConstraint(name = "uk_payments_order_id", columnNames = ["order_id"])],
)
class PaymentModel protected constructor(
    orderId: Long,
    userId: Long,
    cardType: CardType,
    cardNo: String,
    amount: Long,
) : BaseEntity() {
    @Column(name = "order_id", nullable = false)
    var orderId: Long = orderId
        protected set

    @Column(name = "user_id", nullable = false)
    var userId: Long = userId
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false)
    var cardType: CardType = cardType
        protected set

    @Column(name = "card_no", nullable = false)
    var cardNo: String = cardNo
        protected set

    @Column(name = "amount", nullable = false)
    var amount: Long = amount
        protected set

    @Column(name = "transaction_key")
    var transactionKey: String? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: PaymentStatus = PaymentStatus.PENDING
        protected set

    @Column(name = "reason")
    var reason: String? = null
        protected set

    init {
        if (amount <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "결제 금액은 양의 정수여야 합니다.")
        }
        if (!REGEX_CARD_NO.matches(cardNo)) {
            throw CoreException(ErrorType.BAD_REQUEST, "카드 번호는 xxxx-xxxx-xxxx-xxxx 형식이어야 합니다.")
        }
    }

    /**
     * PG 요청 접수 시 발급받은 거래 키를 보관한다.
     * 같은 키로 재접수되면 멱등하게 처리하고, 다른 키가 이미 있으면 충돌로 간주한다.
     */
    fun assignTransactionKey(key: String) {
        val current = transactionKey
        if (current == key) return
        if (current != null) {
            throw CoreException(ErrorType.CONFLICT, "이미 다른 거래 키가 보관된 결제입니다.")
        }
        transactionKey = key
    }

    /**
     * 결제 성공으로 확정한다. 이미 SUCCESS면 멱등 처리, FAILED면 충돌로 간주한다.
     */
    fun markSuccess() {
        when (status) {
            PaymentStatus.SUCCESS -> return
            PaymentStatus.FAILED -> throw CoreException(ErrorType.CONFLICT, "이미 실패로 확정된 결제입니다.")
            PaymentStatus.PENDING -> {
                status = PaymentStatus.SUCCESS
                reason = null
            }
        }
    }

    /**
     * 결제 실패로 확정한다. 이미 FAILED면 멱등 처리, SUCCESS면 충돌로 간주한다.
     */
    fun markFailed(reason: String?) {
        when (status) {
            PaymentStatus.FAILED -> return
            PaymentStatus.SUCCESS -> throw CoreException(ErrorType.CONFLICT, "이미 성공으로 확정된 결제입니다.")
            PaymentStatus.PENDING -> {
                status = PaymentStatus.FAILED
                this.reason = reason
            }
        }
    }

    /**
     * 재결제를 위해 결제를 PENDING으로 되돌린다. 이미 PENDING이면 멱등 처리, SUCCESS면 충돌로 간주한다.
     */
    fun reopen() {
        when (status) {
            PaymentStatus.PENDING -> return
            PaymentStatus.SUCCESS -> throw CoreException(ErrorType.CONFLICT, "이미 성공으로 확정된 결제는 재개할 수 없습니다.")
            PaymentStatus.FAILED -> {
                status = PaymentStatus.PENDING
                transactionKey = null
                reason = null
            }
        }
    }

    companion object {
        private val REGEX_CARD_NO = Regex("^\\d{4}-\\d{4}-\\d{4}-\\d{4}$")

        fun create(
            orderId: Long,
            userId: Long,
            cardType: CardType,
            cardNo: String,
            amount: Long,
        ): PaymentModel = PaymentModel(orderId, userId, cardType, cardNo, amount)
    }
}
