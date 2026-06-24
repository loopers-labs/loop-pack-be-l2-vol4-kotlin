package com.loopers.domain.payment

import com.loopers.domain.order.OrderStatus
import com.loopers.domain.order.model.Order
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

class Payment(
    val id: Long = 0L,
    val orderId: Long,
    val orderNumber: String,
    val memberId: Long,
    val amount: Long,
    val cardType: CardType,
    val cardNo: String,
    status: PaymentStatus = PaymentStatus.REQUESTING,
    transactionKey: String? = null,
    reason: String? = null,
) {
    var status: PaymentStatus = status
        private set

    var transactionKey: String? = transactionKey
        private set

    var reason: String? = reason
        private set

    init {
        if (orderId <= 0L) {
            throw CoreException(ErrorType.BAD_REQUEST, "Order id must be positive.")
        }
        if (orderNumber.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "Order number must not be blank.")
        }
        if (memberId <= 0L) {
            throw CoreException(ErrorType.BAD_REQUEST, "Member id must be positive.")
        }
        if (amount <= 0L) {
            throw CoreException(ErrorType.BAD_REQUEST, "Payment amount must be positive.")
        }
        if (!CARD_NO_PATTERN.matches(cardNo)) {
            throw CoreException(ErrorType.BAD_REQUEST, "Card number must match xxxx-xxxx-xxxx-xxxx.")
        }
    }

    fun markPending(transactionKey: String, reason: String?) {
        if (transactionKey.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "Transaction key must not be blank.")
        }
        if (status !in setOf(PaymentStatus.REQUESTING, PaymentStatus.PENDING_CONFIRMATION)) {
            throw CoreException(ErrorType.BAD_REQUEST, "Payment request cannot be accepted from current status.")
        }

        this.transactionKey = transactionKey
        status = PaymentStatus.PENDING
        this.reason = reason
    }

    fun markPendingConfirmation(reason: String?) {
        if (status != PaymentStatus.REQUESTING) {
            throw CoreException(ErrorType.BAD_REQUEST, "Payment cannot wait for confirmation from current status.")
        }

        status = PaymentStatus.PENDING_CONFIRMATION
        this.reason = reason
    }

    fun markRequestFailed(reason: String?) {
        if (status != PaymentStatus.REQUESTING) {
            throw CoreException(ErrorType.BAD_REQUEST, "Payment request cannot fail from current status.")
        }

        status = PaymentStatus.REQUEST_FAILED
        this.reason = reason
    }

    fun succeed(transactionKey: String, reason: String?) {
        if (transactionKey.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "Transaction key must not be blank.")
        }
        if (status == PaymentStatus.SUCCESS) {
            return
        }
        if (status !in setOf(PaymentStatus.PENDING, PaymentStatus.PENDING_CONFIRMATION)) {
            throw CoreException(ErrorType.BAD_REQUEST, "Payment cannot succeed from current status.")
        }

        this.transactionKey = transactionKey
        status = PaymentStatus.SUCCESS
        this.reason = reason
    }

    fun fail(transactionKey: String, reason: String?) {
        if (transactionKey.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "Transaction key must not be blank.")
        }
        if (status == PaymentStatus.FAILED) {
            return
        }
        if (status !in setOf(PaymentStatus.PENDING, PaymentStatus.PENDING_CONFIRMATION)) {
            throw CoreException(ErrorType.BAD_REQUEST, "Payment cannot fail from current status.")
        }

        this.transactionKey = transactionKey
        status = PaymentStatus.FAILED
        this.reason = reason
    }

    fun isTerminal(): Boolean = status in setOf(
        PaymentStatus.REQUEST_FAILED,
        PaymentStatus.SUCCESS,
        PaymentStatus.FAILED,
    )

    companion object {
        private val CARD_NO_PATTERN = Regex("^\\d{4}-\\d{4}-\\d{4}-\\d{4}$")

        fun request(
            order: Order,
            cardType: CardType,
            cardNo: String,
        ): Payment {
            if (order.status != OrderStatus.PENDING_PAYMENT) {
                throw CoreException(ErrorType.BAD_REQUEST, "Order is not pending payment.")
            }

            return Payment(
                orderId = order.id,
                orderNumber = order.orderNumber,
                memberId = order.memberId,
                amount = order.totalAmount,
                cardType = cardType,
                cardNo = cardNo,
            )
        }
    }
}
