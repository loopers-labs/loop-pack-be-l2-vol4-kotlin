package com.loopers.infrastructure.payment

import com.loopers.application.payment.PaymentCommand
import com.loopers.application.payment.PaymentGateway
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.RestTemplate
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Configuration
@EnableConfigurationProperties(PgSimulatorProperties::class)
class PgSimulatorRestTemplateConfig {
    @Bean
    fun pgSimulatorRestTemplate(
        restTemplateBuilder: RestTemplateBuilder,
        properties: PgSimulatorProperties,
    ): RestTemplate =
        restTemplateBuilder
            .setConnectTimeout(properties.timeout)
            .setReadTimeout(properties.timeout)
            .build()

    @Bean
    fun pgSimulatorExecutorService(): ExecutorService =
        Executors.newCachedThreadPool()

    @Bean
    fun pgSimulatorResilience(
        properties: PgSimulatorProperties,
        @Qualifier("pgSimulatorExecutorService") executorService: ExecutorService,
    ): PgSimulatorResilience =
        PgSimulatorResilience.from(properties, executorService)

    @Bean
    fun pgSimulatorExecutorShutdown(
        @Qualifier("pgSimulatorExecutorService") executorService: ExecutorService,
    ): DisposableBean =
        DisposableBean { executorService.shutdown() }
}

@Component
class PgSimulatorPaymentGateway(
    private val restTemplate: RestTemplate,
    private val properties: PgSimulatorProperties,
    private val resilience: PgSimulatorResilience,
) : PaymentGateway {
    override fun approve(command: PaymentCommand.Approve): PaymentGateway.PgResult =
        runCatching {
            val response = resilience.execute {
                restTemplate.exchange(
                    "${properties.baseUrl}/api/v1/payments",
                    HttpMethod.POST,
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
                    responseType<PgSimulatorPaymentDto.TransactionResponse>(),
                )
            }
            val data = response.body?.data
            val transactionKey = data?.transactionKey
            val status = data?.status ?: "UNKNOWN"
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
            val response = resilience.execute {
                restTemplate.exchange(
                    "${properties.baseUrl}/api/v1/payments/$transactionKey",
                    HttpMethod.GET,
                    HttpEntity<Unit>(headers(command.userId)),
                    responseType<PgSimulatorPaymentDto.TransactionDetailResponse>(),
                )
            }
            val data = response.body?.data
            val status = data?.status ?: "UNKNOWN"
            val amount = data?.amount
            val reason = data?.reason
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

    override fun findByOrder(command: PaymentCommand.FindByOrder): List<PaymentGateway.PgTransaction> =
        runCatching {
            val response = resilience.execute {
                restTemplate.exchange(
                    "${properties.baseUrl}/api/v1/payments?orderId=${command.orderId}",
                    HttpMethod.GET,
                    HttpEntity<Unit>(headers(command.userId)),
                    responseType<PgSimulatorPaymentDto.OrderResponse>(),
                )
            }
            response.body?.data?.transactions.orEmpty().map { transaction ->
                PaymentGateway.PgTransaction(
                    transactionKey = transaction.transactionKey,
                    status = transaction.status,
                    amount = transaction.amount,
                    failureReason = transaction.reason,
                    rawResponseSummary = "pg simulator order lookup status=${transaction.status} " +
                        "transactionKey=${transaction.transactionKey} reason=${transaction.reason}",
                )
            }
        }.getOrElse {
            emptyList()
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

    private inline fun <reified T> responseType() =
        object : ParameterizedTypeReference<PgSimulatorPaymentDto.ApiResponse<T>>() {}

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
