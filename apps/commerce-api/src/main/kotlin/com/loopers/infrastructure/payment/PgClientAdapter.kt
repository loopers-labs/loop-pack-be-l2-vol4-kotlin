package com.loopers.infrastructure.payment

import com.loopers.domain.payment.PaymentFailureReason
import com.loopers.domain.payment.PgClient
import com.loopers.domain.payment.PgPaymentResult
import com.loopers.domain.payment.PgRequestCommand
import com.loopers.domain.payment.PgStatus
import org.springframework.stereotype.Component

@Component
class PgClientAdapter(
    private val pgFeignClient: PgFeignClient,
) : PgClient {
    override fun requestPayment(command: PgRequestCommand): PgPaymentResult {
        val response = pgFeignClient.requestPayment(
            userId = command.userId.toString(),
            request = PgPaymentRequest(
                orderId = command.orderId.toString(),
                cardType = command.cardType.name,
                cardNo = command.cardNo,
                amount = command.amount,
                callbackUrl = command.callbackUrl,
            ),
        )
        return response.data?.let { toResult(it) } ?: PgPaymentResult.unknown()
    }

    override fun getByTransactionKey(transactionKey: String): PgPaymentResult {
        val response = pgFeignClient.getByTransactionKey(userId = "reconciler", transactionKey = transactionKey)
        return response.data?.let { toResult(it) } ?: PgPaymentResult.unknown()
    }

    override fun findByOrderId(orderId: Long): PgPaymentResult? {
        val response = pgFeignClient.findByOrderId(userId = "reconciler", orderId = orderId.toString())
        val tx = response.data?.transactions?.firstOrNull() ?: return null
        return toResult(tx)
    }

    private fun toResult(tx: PgTransactionResponse): PgPaymentResult {
        val status = when (tx.status?.uppercase()) {
            "SUCCESS" -> PgStatus.SUCCESS
            "FAILED" -> PgStatus.FAILED
            else -> PgStatus.PENDING
        }
        val reason = if (status == PgStatus.FAILED) PaymentFailureReason.fromPgReason(tx.reason) else null
        return PgPaymentResult(transactionKey = tx.transactionKey, status = status, failureReason = reason)
    }
}
