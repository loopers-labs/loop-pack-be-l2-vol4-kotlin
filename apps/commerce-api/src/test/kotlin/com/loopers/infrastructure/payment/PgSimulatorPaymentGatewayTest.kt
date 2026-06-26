package com.loopers.infrastructure.payment

import com.loopers.application.payment.PaymentCommand
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import java.time.Duration

class PgSimulatorPaymentGatewayTest {
    private val restTemplate = RestTemplateBuilder().build()
    private val server = MockRestServiceServer.bindTo(restTemplate).build()

    private fun gateway(
        properties: PgSimulatorProperties = PgSimulatorProperties(
            baseUrl = "http://pg.local",
            callbackUrl = "http://localhost:8080/api/v1/payments/callback",
        ),
    ): PgSimulatorPaymentGateway =
        PgSimulatorPaymentGateway(
            restTemplate = restTemplate,
            properties = properties,
            resilience = PgSimulatorResilience.from(properties),
        )

    @Test
    fun approveCreatesPgSimulatorTransactionAndReturnsPendingTransactionKey() {
        server.expect(requestTo("http://pg.local/api/v1/payments"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-USER-ID", "1"))
            .andRespond(
                withSuccess(
                    """
                    {
                      "meta": {"result": "SUCCESS", "errorCode": null, "message": null},
                      "data": {"transactionKey": "20260624:TR:abc123", "status": "PENDING", "reason": null}
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = gateway().approve(
            PaymentCommand.Approve(
                userId = 1L,
                orderId = 100000L,
                paymentRequestId = "order-100000-request",
                cardType = "SAMSUNG",
                cardNo = "1234-5678-1234-5678",
                amount = 5000L,
            ),
        )

        assertAll(
            { assertThat(result.success).isTrue() },
            { assertThat(result.pgStatus).isEqualTo("PENDING") },
            { assertThat(result.pgTransactionId).isEqualTo("20260624:TR:abc123") },
            { assertThat(result.approvedAmount).isNull() },
            { assertThat(result.rawResponseSummary).contains("20260624:TR:abc123") },
        )
        server.verify()
    }

    @Test
    fun verifyReadsTransactionDetailByTransactionKey() {
        server.expect(requestTo("http://pg.local/api/v1/payments/20260624:TR:abc123"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("X-USER-ID", "1"))
            .andRespond(
                withSuccess(
                    """
                    {
                      "meta": {"result": "SUCCESS", "errorCode": null, "message": null},
                      "data": {
                        "transactionKey": "20260624:TR:abc123",
                        "orderId": "100000",
                        "cardType": "SAMSUNG",
                        "cardNo": "1234-5678-1234-5678",
                        "amount": 5000,
                        "status": "SUCCESS",
                        "reason": "정상 승인되었습니다."
                      }
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = gateway().verify(
            PaymentCommand.Verify(
                userId = 1L,
                orderId = 100000L,
                paymentRequestId = "order-100000-request",
                paymentKey = "20260624:TR:abc123",
                pgTransactionId = "20260624:TR:abc123",
                amount = 5000L,
            ),
        )

        assertAll(
            { assertThat(result.success).isTrue() },
            { assertThat(result.pgStatus).isEqualTo("SUCCESS") },
            { assertThat(result.pgTransactionId).isEqualTo("20260624:TR:abc123") },
            { assertThat(result.approvedAmount).isEqualTo(5000L) },
        )
        server.verify()
    }

    @Test
    fun approveRetriesTransientPgFailuresAndThenReturnsTransactionKey() {
        server.expect(requestTo("http://pg.local/api/v1/payments"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withServerError())
        server.expect(requestTo("http://pg.local/api/v1/payments"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withServerError())
        server.expect(requestTo("http://pg.local/api/v1/payments"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(
                withSuccess(
                    """
                    {
                      "meta": {"result": "SUCCESS", "errorCode": null, "message": null},
                      "data": {"transactionKey": "20260624:TR:retry-ok", "status": "PENDING", "reason": null}
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = gateway(
            PgSimulatorProperties(
                baseUrl = "http://pg.local",
                callbackUrl = "http://localhost:8080/api/v1/payments/callback",
                retry = PgSimulatorProperties.RetryPolicy(
                    maxRetries = 3,
                    waitDuration = Duration.ZERO,
                ),
            ),
        ).approve(
            PaymentCommand.Approve(
                userId = 1L,
                orderId = 100000L,
                paymentRequestId = "order-100000-request",
                cardType = "SAMSUNG",
                cardNo = "1234-5678-1234-5678",
                amount = 5000L,
            ),
        )

        assertAll(
            { assertThat(result.success).isTrue() },
            { assertThat(result.pgStatus).isEqualTo("PENDING") },
            { assertThat(result.pgTransactionId).isEqualTo("20260624:TR:retry-ok") },
        )
        server.verify()
    }

    @Test
    fun findByOrderReadsPgSimulatorTransactionsForOrder() {
        server.expect(requestTo("http://pg.local/api/v1/payments?orderId=100000"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("X-USER-ID", "1"))
            .andRespond(
                withSuccess(
                    """
                    {
                      "meta": {"result": "SUCCESS", "errorCode": null, "message": null},
                      "data": {
                        "orderId": "100000",
                        "transactions": [
                          {
                            "transactionKey": "20260624:TR:lookup-ok",
                            "orderId": "100000",
                            "cardType": "SAMSUNG",
                            "cardNo": "1234-5678-1234-5678",
                            "amount": 5000,
                            "status": "SUCCESS",
                            "reason": "정상 승인되었습니다."
                          }
                        ]
                      }
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val transactions = gateway().findByOrder(
            PaymentCommand.FindByOrder(
                userId = 1L,
                orderId = 100000L,
            ),
        )

        assertAll(
            { assertThat(transactions).hasSize(1) },
            { assertThat(transactions.single().transactionKey).isEqualTo("20260624:TR:lookup-ok") },
            { assertThat(transactions.single().status).isEqualTo("SUCCESS") },
            { assertThat(transactions.single().amount).isEqualTo(5000L) },
            { assertThat(transactions.single().failureReason).isEqualTo("정상 승인되었습니다.") },
        )
        server.verify()
    }
}
