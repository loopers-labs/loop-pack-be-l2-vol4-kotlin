package com.loopers.payment.interfaces

import com.loopers.account.infrastructure.security.AccountAuthenticationAttributes.ACCOUNT_ID
import com.loopers.payment.application.PaymentCommand
import com.loopers.payment.application.PaymentFacade
import com.loopers.payment.application.PaymentInfo
import com.loopers.payment.domain.CardType
import com.loopers.payment.domain.PaymentStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/payments")
class PaymentController(private val paymentFacade: PaymentFacade) {
    @PostMapping
    fun pay(
        @RequestAttribute(ACCOUNT_ID) userId: Long,
        @RequestBody request: PaymentRequest,
    ): PaymentResponse =
        PaymentResponse.from(paymentFacade.pay(request.toCommand(userId)))
}

data class PaymentRequest(val orderKey: String, val cardType: CardType, val cardNo: String) {
    fun toCommand(userId: Long): PaymentCommand =
        PaymentCommand(userId = userId, orderKey = orderKey, cardType = cardType, cardNo = cardNo)
}

data class PaymentResponse(val paymentId: Long, val status: PaymentStatus) {
    companion object {
        fun from(info: PaymentInfo): PaymentResponse =
            PaymentResponse(paymentId = info.paymentId, status = info.status)
    }
}
