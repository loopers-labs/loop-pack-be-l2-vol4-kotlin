package com.loopers.interfaces.api.payment

import com.loopers.application.payment.PaymentInfo
import com.loopers.application.payment.SyncPaymentResultCommand
import com.loopers.application.payment.usecase.RequestPaymentUsecase
import com.loopers.application.payment.usecase.SyncPaymentResultUsecase
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PgClient
import com.loopers.domain.payment.PgStatus
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/payments")
class PaymentV1Controller(
    private val requestPaymentUsecase: RequestPaymentUsecase,
    private val syncPaymentResultUsecase: SyncPaymentResultUsecase,
    private val paymentRepository: PaymentRepository,
    private val pgClient: PgClient,
) {
    @PostMapping
    fun request(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") password: String,
        @RequestBody request: PaymentV1Dto.PaymentRequest,
    ): ApiResponse<PaymentV1Dto.PaymentResponse> =
        requestPaymentUsecase.request(request.toCommand(loginId, password))
            .let { PaymentV1Dto.PaymentResponse.from(it) }
            .let { ApiResponse.success(it) }

    @PostMapping("/callback")
    fun callback(
        @RequestBody request: PaymentV1Dto.CallbackRequest,
    ): ApiResponse<Any> {
        syncPaymentResultUsecase.apply(request.toCommand())
        return ApiResponse.success()
    }

    // 운영 수동 복구: PG 재조회 후 동일 경로로 반영.
    @PostMapping("/{id}/sync")
    fun sync(@PathVariable("id") id: Long): ApiResponse<PaymentV1Dto.PaymentResponse> {
        val payment = paymentRepository.findById(id)
            ?: throw CoreException(ErrorType.NOT_FOUND, "결제를 찾을 수 없습니다.")
        val result = payment.transactionKey
            ?.let { pgClient.getByTransactionKey(it) }
            ?: pgClient.findByOrderId(payment.orderId)
        if (result != null && result.status != PgStatus.PENDING) {
            syncPaymentResultUsecase.apply(
                SyncPaymentResultCommand(
                    transactionKey = result.transactionKey ?: payment.transactionKey,
                    orderId = payment.orderId,
                    status = result.status,
                    failureReason = result.failureReason,
                ),
            )
        }
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(PaymentInfo.from(paymentRepository.findById(id)!!)))
    }
}
