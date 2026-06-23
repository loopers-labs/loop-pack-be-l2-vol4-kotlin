package com.loopers.infrastructure.payment

import com.loopers.application.payment.PaymentCommand
import com.loopers.application.payment.PaymentStatus
import com.loopers.domain.order.OrderAmount
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class PgPaymentGatewayTest {

    private lateinit var mockServer: MockRestServiceServer
    private lateinit var pgPaymentGateway: PgPaymentGateway

    @BeforeEach
    fun setUp() {
        val restClientBuilder = RestClient.builder().baseUrl("http://localhost:8082")
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build()
        pgPaymentGateway = PgPaymentGateway(restClientBuilder.build())
    }

    @DisplayName("PG에 결제 요청을 보내면 transactionKey와 PENDING 상태를 반환한다")
    @Test
    fun pay_returnsPendingResult_whenPgRespondsSuccessfully() {
        // arrange
        val command = PaymentCommand(
            orderId = 100001L,
            userId = 1L,
            amount = OrderAmount(5000L),
            cardType = "SAMSUNG",
            cardNo = "1234-5678-9012-3456",
            callbackUrl = "http://localhost:8080/api/v1/payments/callback",
        )

        mockServer.expect(requestTo("http://localhost:8082/api/v1/payments"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(
                withSuccess(
                    """
                    {
                        "meta": {
                            "result": "SUCCESS",
                            "errorCode": null,
                            "message": null
                        },
                        "data": {
                            "transactionKey": "20250623:TR:abc123",
                            "status": "PENDING",
                            "reason": null
                        }
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        // act
        val result = pgPaymentGateway.pay(command)

        // assert
        assertThat(result.transactionKey).isEqualTo("20250623:TR:abc123")
        assertThat(result.status).isEqualTo(PaymentStatus.PENDING)
        mockServer.verify()
    }
}
