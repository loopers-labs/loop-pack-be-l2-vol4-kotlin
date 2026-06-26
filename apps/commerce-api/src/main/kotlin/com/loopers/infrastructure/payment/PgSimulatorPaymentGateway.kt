package com.loopers.infrastructure.payment

import com.loopers.application.payment.PaymentCommand
import com.loopers.application.payment.PaymentGateway
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.RestTemplate

@Configuration
class PgSimulatorRestTemplateConfig {
    @Bean
    fun pgSimulatorRestTemplate(restTemplateBuilder: RestTemplateBuilder): RestTemplate =
        restTemplateBuilder.build()
}

@Component
@EnableConfigurationProperties(PgSimulatorProperties::class)
class PgSimulatorPaymentGateway(
    private val restTemplate: RestTemplate,
    private val properties: PgSimulatorProperties,
) : PaymentGateway {
    override fun approve(command: PaymentCommand.Approve): PaymentGateway.PgResult =
        runCatching {
            val response = restTemplate.postForObject(
                "${properties.baseUrl}/api/v1/payments",
                HttpEntity(
                    PgSimulatorPaymentDto.PaymentRequest(
                        orderId = command.orderId.toString(),
                        cardType = command.cardType,
                        cardNo = command.cardNo,
                        amount = command.amount,
                        callbackUrl = properties.callbackUrl,
                    ),
                    headers(command.userId),
                ),
                PgSimulatorPaymentDto.ApiResponse::class.java,
            )
            val data = response?.data as? Map<*, *>
            val transactionKey = data?.get("transactionKey") as? String
            val status = data?.get("status") as? String ?: "UNKNOWN"
            PaymentGateway.PgResult(
                success = transactionKey != null,
                pgStatus = status,
                pgTransactionId = transactionKey,
                approvedAmount = null,
                failureReason = null,
                rawResponseSummary = "pg simulator request status=$status transactionKey=$transactionKey",
            )
        }.getOrElse { throwable ->
            failedResult("REQUEST_FAILED", throwable)
        }

    override fun verify(command: PaymentCommand.Verify): PaymentGateway.PgResult {
        val transactionKey = command.pgTransactionId ?: command.paymentKey
            ?: return PaymentGateway.PgResult(
                success = false,
                pgStatus = "MISSING_TRANSACTION_KEY",
                pgTransactionId = null,
                approvedAmount = null,
                failureReason = "PG 거래 식별자가 없습니다.",
                rawResponseSummary = "pg simulator verify missing transaction key",
            )

        return runCatching {
            val response = restTemplate.exchange(
                "${properties.baseUrl}/api/v1/payments/$transactionKey",
                HttpMethod.GET,
                HttpEntity<Unit>(headers(command.userId)),
                PgSimulatorPaymentDto.ApiResponse::class.java,
            )
            val data = response.body?.data as? Map<*, *>
            val status = data?.get("status") as? String ?: "UNKNOWN"
            val amount = (data?.get("amount") as? Number)?.toLong()
            val reason = data?.get("reason") as? String
            PaymentGateway.PgResult(
                success = status == "SUCCESS",
                pgStatus = status,
                pgTransactionId = transactionKey,
                approvedAmount = amount.takeIf { status == "SUCCESS" },
                failureReason = reason.takeIf { status != "SUCCESS" },
                rawResponseSummary = "pg simulator verify status=$status transactionKey=$transactionKey reason=$reason",
            )
        }.getOrElse { throwable ->
            failedResult("VERIFY_FAILED", throwable)
        }
    }

    override fun cancel(command: PaymentCommand.Cancel): PaymentGateway.PgResult =
        PaymentGateway.PgResult(
            success = false,
            pgStatus = "CANCEL_UNSUPPORTED",
            pgTransactionId = command.pgTransactionId,
            approvedAmount = null,
            failureReason = "PG simulator는 결제 취소 API를 제공하지 않습니다.",
            rawResponseSummary = "pg simulator cancel unsupported",
        )

    private fun headers(userId: Long): HttpHeaders =
        HttpHeaders().apply {
            add("X-USER-ID", userId.toString())
        }

    private fun failedResult(pgStatus: String, throwable: Throwable): PaymentGateway.PgResult {
        val reason = when (throwable) {
            is RestClientResponseException -> throwable.responseBodyAsString.ifBlank { throwable.message }
            else -> throwable.message
        } ?: "PG simulator 호출에 실패했습니다."
        return PaymentGateway.PgResult(
            success = false,
            pgStatus = pgStatus,
            pgTransactionId = null,
            approvedAmount = null,
            failureReason = reason.take(500),
            rawResponseSummary = "pg simulator error status=$pgStatus reason=${reason.take(500)}",
        )
    }
}
