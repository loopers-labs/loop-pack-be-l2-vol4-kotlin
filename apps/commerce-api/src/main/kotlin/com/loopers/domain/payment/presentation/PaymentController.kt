package com.loopers.domain.payment.presentation

import com.loopers.domain.payment.application.PaymentFacade
import com.loopers.domain.payment.presentation.request.PaymentCallbackRequest
import com.loopers.domain.payment.presentation.request.PaymentRequest
import com.loopers.domain.payment.presentation.response.PaymentRecoveryResponse
import com.loopers.domain.payment.presentation.response.PaymentResponse
import com.loopers.domain.user.application.info.UserInfo
import com.loopers.domain.user.presentation.auth.LoginUser
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.swagger.v3.oas.annotations.Parameter
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@RestController
@RequestMapping("/api/v1/payments")
@Validated
class PaymentController(
    private val paymentFacade: PaymentFacade,
    @Value("\${payment.callback-secret}") private val callbackSecret: String,
) {
    @PostMapping
    fun request(
        @Parameter(hidden = true) @LoginUser user: UserInfo,
        @Valid @RequestBody request: PaymentRequest,
    ): ApiResponse<PaymentResponse> =
        paymentFacade.request(request.toCommand(user.id))
            .let { PaymentResponse.from(it) }
            .let { ApiResponse.success(it) }

    @PostMapping("/callback")
    fun callback(
        @RequestHeader(value = CALLBACK_SECRET_HEADER, required = false) secret: String?,
        @Valid @RequestBody request: PaymentCallbackRequest,
    ): ApiResponse<PaymentResponse> {
        verifyCallbackSecret(secret)
        return paymentFacade.handleCallback(request.toCommand())
            .let { PaymentResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    private fun verifyCallbackSecret(secret: String?) {
        val provided = secret?.toByteArray(StandardCharsets.UTF_8) ?: ByteArray(0)
        val expected = callbackSecret.toByteArray(StandardCharsets.UTF_8)
        if (!MessageDigest.isEqual(provided, expected)) {
            throw CoreException(ErrorType.UNAUTHORIZED, "콜백 시크릿이 일치하지 않습니다.")
        }
    }

    @PostMapping("/recovery")
    fun recover(): ApiResponse<PaymentRecoveryResponse> =
        paymentFacade.recoverUnknownPayments()
            .let { PaymentRecoveryResponse.from(it) }
            .let { ApiResponse.success(it) }

    @PostMapping("/result-events/drain")
    fun drainResultEvents(): ApiResponse<Int> =
        ApiResponse.success(paymentFacade.consumeResultEvents())

    companion object {
        const val CALLBACK_SECRET_HEADER = "X-Payment-Callback-Secret"
    }
}
