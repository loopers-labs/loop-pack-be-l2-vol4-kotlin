package com.loopers.infrastructure.payment

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PaymentFailureReason
import com.loopers.domain.payment.PgClient
import com.loopers.domain.payment.PgOrderLookup
import com.loopers.domain.payment.PgRequestCommand
import com.loopers.domain.payment.PgStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.test.context.TestPropertySource

@SpringBootTest
@AutoConfigureWireMock(port = 0)
@TestPropertySource(properties = ["pg.base-url=http://localhost:\${wiremock.server.port}"])
class PgClientAdapterTest @Autowired constructor(
    private val pgClient: PgClient,
) {
    private fun command() = PgRequestCommand(
        orderId = 1L,
        userId = 10L,
        amount = 1000L,
        cardType = CardType.SAMSUNG,
        cardNo = "1234-5678-9012-3456",
        callbackUrl = "http://localhost:8080/api/v1/payments/callback",
    )

    @Test
    fun `요청 접수 성공 시 transactionKey 를 담은 PENDING 결과를 반환한다`() {
        stubFor(
            post(urlPathEqualTo("/api/v1/payments")).willReturn(
                aResponse().withHeader("Content-Type", "application/json")
                    .withBody("""{"meta":{"result":"SUCCESS"},"data":{"transactionKey":"tx-1","status":"PENDING"}}"""),
            ),
        )

        val result = pgClient.requestPayment(command())

        assertThat(result.transactionKey).isEqualTo("tx-1")
        assertThat(result.status).isEqualTo(PgStatus.PENDING)
    }

    @Test
    fun `조회 시 실패 상태와 사유를 도메인 enum 으로 변환한다`() {
        stubFor(
            get(urlPathEqualTo("/api/v1/payments/tx-1")).willReturn(
                aResponse().withHeader("Content-Type", "application/json")
                    .withBody("""{"meta":{"result":"SUCCESS"},"data":{"transactionKey":"tx-1","status":"FAILED","reason":"LIMIT_EXCEEDED"}}"""),
            ),
        )

        val result = pgClient.getByTransactionKey("tx-1")

        assertThat(result.status).isEqualTo(PgStatus.FAILED)
        assertThat(result.failureReason).isEqualTo(PaymentFailureReason.LIMIT_EXCEEDED)
    }

    @Test
    fun `타임아웃이면 서킷 fallback 으로 결과 불명(PENDING) 을 반환한다`() {
        stubFor(
            post(urlPathEqualTo("/api/v1/payments")).willReturn(
                aResponse().withFixedDelay(5000) // TimeLimiter(2s) 초과 → 서킷 fallback
                    .withHeader("Content-Type", "application/json")
                    .withBody("""{"meta":{"result":"SUCCESS"},"data":{"transactionKey":"tx-late"}}"""),
            ),
        )

        val result = pgClient.requestPayment(command())

        assertThat(result.transactionKey).isNull()
        assertThat(result.status).isEqualTo(PgStatus.PENDING)
    }

    @Test
    fun `PG 응답에 결제건이 있으면 Found 를 반환한다`() {
        stubFor(
            get(urlPathEqualTo("/api/v1/payments")).willReturn(
                aResponse().withHeader("Content-Type", "application/json")
                    .withBody("""{"meta":{"result":"SUCCESS"},"data":{"transactions":[{"transactionKey":"tx","status":"SUCCESS"}]}}"""),
            ),
        )

        assertThat(pgClient.findByOrderId(1L)).isEqualTo(PgOrderLookup.Found(com.loopers.domain.payment.PgPaymentResult(transactionKey = "tx", status = PgStatus.SUCCESS, failureReason = null)))
    }

    @Test
    fun `PG 응답에 결제건이 없으면 미접수 확정이다`() {
        stubFor(
            get(urlPathEqualTo("/api/v1/payments")).willReturn(
                aResponse().withHeader("Content-Type", "application/json")
                    .withBody("""{"meta":{"result":"SUCCESS"},"data":{"transactions":[]}}"""),
            ),
        )

        assertThat(pgClient.findByOrderId(1L)).isEqualTo(PgOrderLookup.NotAccepted)
    }

    @Test
    fun `조회 실패(서킷 fallback)는 불명이다`() {
        stubFor(
            get(urlPathEqualTo("/api/v1/payments")).willReturn(
                aResponse().withFixedDelay(5000)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""{"data":{"transactions":[]}}"""),
            ),
        )

        assertThat(pgClient.findByOrderId(1L)).isInstanceOf(PgOrderLookup.Unknown::class.java)
    }
}
