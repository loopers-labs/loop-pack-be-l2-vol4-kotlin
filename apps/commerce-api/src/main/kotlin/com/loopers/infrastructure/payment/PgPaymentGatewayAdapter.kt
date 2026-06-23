package com.loopers.infrastructure.payment

import com.loopers.domain.payment.PaymentGatewayPort
import com.loopers.domain.payment.PaymentGatewayRequest
import com.loopers.domain.payment.PaymentGatewayResponse
import com.loopers.domain.payment.PaymentStatus
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class PgPaymentGatewayAdapter(
    private val pgPaymentClient: PgPaymentClient,
    @Value("\${pg-simulator.callback-url}") private val callbackUrl: String,
) : PaymentGatewayPort {

    override fun requestPayment(request: PaymentGatewayRequest): PaymentGatewayResponse {
        val response = pgPaymentClient.requestPayment(
            userId = request.userId.toString(),
            request = PgPaymentRequest(
                orderId = request.orderId.toString(),
                cardType = CardType.from(request.cardType).name,
                cardNo = request.cardNo,
                amount = request.amount,
                callbackUrl = callbackUrl,
            ),
        )
        val data = response.data
            ?: throw CoreException(ErrorType.INTERNAL_ERROR, "PG 응답이 올바르지 않습니다.")
        return PaymentGatewayResponse(
            transactionKey = data.transactionKey,
            status = PaymentStatus.valueOf(data.status.uppercase()),
        )
    }
}
