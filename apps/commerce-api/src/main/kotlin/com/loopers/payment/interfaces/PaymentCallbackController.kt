package com.loopers.payment.interfaces

import com.loopers.payment.application.PaymentCallbackCommand
import com.loopers.payment.application.PaymentResultStatus
import com.loopers.payment.application.PaymentService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/payments")
class PaymentCallbackController(
    private val paymentService: PaymentService,
) {
    @PostMapping("/callback")
    fun callback(
        @RequestBody request: PaymentCallbackRequest,
    ) {
        paymentService.handleCallback(request.toCommand())
    }
}

data class PaymentCallbackRequest(
    val transactionKey: String,
    val orderId: String,
    val amount: Long,
    val status: PaymentResultStatus,
    val reason: String?,
) {
    fun toCommand(): PaymentCallbackCommand =
        PaymentCallbackCommand(
            orderKey = orderId,
            transactionKey = transactionKey,
            status = status,
            reason = reason,
        )
}
