package com.loopers.application.payment

import com.loopers.domain.order.OrderAmount

interface PaymentGateway {
    fun pay(command: PaymentCommand): PaymentResult

    fun cancel(command: PaymentCancelCommand)

    fun getTransactionStatus(transactionKey: String): PaymentTransactionInfo

    fun getTransactionsByOrderId(orderId: String): List<PaymentTransactionInfo>
}

data class PaymentCommand(
    val orderId: Long,
    val userId: Long,
    val amount: OrderAmount,
    val cardType: String,
    val cardNo: String,
    val callbackUrl: String,
)

data class PaymentCancelCommand(
    val orderId: Long,
    val userId: Long,
    val amount: OrderAmount,
)

data class PaymentResult(
    val transactionKey: String,
    val status: PaymentStatus,
    val reason: String?,
)

data class PaymentTransactionInfo(
    val transactionKey: String,
    val orderId: String,
    val cardType: String,
    val cardNo: String,
    val amount: Long,
    val status: PaymentStatus,
    val reason: String?,
)

enum class PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED,
}
