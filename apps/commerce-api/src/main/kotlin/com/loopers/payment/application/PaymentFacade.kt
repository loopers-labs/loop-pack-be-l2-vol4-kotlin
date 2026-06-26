package com.loopers.payment.application

import com.loopers.payment.domain.CardType
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class PaymentFacade(
    private val paymentService: PaymentService,
    private val pgPaymentGateway: PgPaymentGateway,
    @Value("\${pg.callback-url}") private val callbackUrl: String,
) {
    fun pay(command: PaymentCommand): PaymentInfo {
        val prepared = paymentService.prepare(
            PaymentPrepareCommand(command.userId, command.orderKey, command.cardType),
        )

        val result = pgPaymentGateway.submit(
            PgSubmitCommand(
                userId = command.userId,
                orderKey = command.orderKey,
                cardType = command.cardType,
                cardNo = command.cardNo,
                amount = prepared.amount,
                callbackUrl = callbackUrl,
            ),
        )

        return paymentService.reflectSubmit(prepared.paymentId, result)
    }
}

data class PaymentCommand(
    val userId: Long,
    val orderKey: String,
    val cardType: CardType,
    val cardNo: String,
)
