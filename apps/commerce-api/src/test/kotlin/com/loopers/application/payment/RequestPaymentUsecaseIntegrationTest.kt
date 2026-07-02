package com.loopers.application.payment

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.loopers.application.payment.usecase.RequestPaymentUsecase
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.test.context.TestPropertySource

@SpringBootTest
@AutoConfigureWireMock(port = 0)
@TestPropertySource(properties = ["pg.base-url=http://localhost:\${wiremock.server.port}"])
class RequestPaymentUsecaseIntegrationTest @Autowired constructor(
    private val requestPaymentUsecase: RequestPaymentUsecase,
    private val paymentRepository: PaymentRepository,
    private val fixtures: PaymentTestFixtures,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() = databaseCleanUp.truncateAllTables()

    @Test
    fun `결제 요청 시 Payment PENDING 저장 후 transactionKey 를 기록한다`() {
        // Arrange
        val ctx = fixtures.pendingOrder()
        stubFor(
            post(urlPathEqualTo("/api/v1/payments")).willReturn(
                aResponse().withHeader("Content-Type", "application/json")
                    .withBody("""{"meta":{"result":"SUCCESS"},"data":{"transactionKey":"tx-1","status":"PENDING"}}"""),
            ),
        )

        // Act
        val info = requestPaymentUsecase.request(
            RequestPaymentCommand(ctx.loginId, ctx.password, ctx.orderId, CardType.SAMSUNG, "1234-5678-9012-3456"),
        )

        // Assert
        assertThat(info.status).isEqualTo(PaymentStatus.PENDING)
        val saved = paymentRepository.findByOrderId(ctx.orderId)
        assertThat(saved?.transactionKey).isEqualTo("tx-1")
        assertThat(saved?.acceptedAt).isNotNull()
    }

    @Test
    fun `PG 타임아웃이어도 결제는 PENDING 으로 접수되고 응답한다`() {
        val ctx = fixtures.pendingOrder()
        stubFor(
            post(urlPathEqualTo("/api/v1/payments")).willReturn(
                aResponse().withFixedDelay(5000).withHeader("Content-Type", "application/json")
                    .withBody("""{"meta":{"result":"SUCCESS"},"data":{"transactionKey":"tx-late"}}"""),
            ),
        )

        val info = requestPaymentUsecase.request(
            RequestPaymentCommand(ctx.loginId, ctx.password, ctx.orderId, CardType.SAMSUNG, "1234-5678-9012-3456"),
        )

        assertThat(info.status).isEqualTo(PaymentStatus.PENDING)
        assertThat(paymentRepository.findByOrderId(ctx.orderId)?.transactionKey).isNull()
    }

    @Test
    fun `PENDING 이 아닌 주문은 결제할 수 없다`() {
        val ctx = fixtures.paidOrder()
        assertThatThrownBy {
            requestPaymentUsecase.request(
                RequestPaymentCommand(ctx.loginId, ctx.password, ctx.orderId, CardType.SAMSUNG, "1234-5678-9012-3456"),
            )
        }.isInstanceOf(CoreException::class.java)
            .satisfies({ assertThat((it as CoreException).errorType).isEqualTo(ErrorType.CONFLICT) })
    }

    @Test
    fun `이미 결제가 진행 중인 주문은 중복 결제할 수 없다`() {
        val ctx = fixtures.pendingOrder()
        stubFor(
            post(urlPathEqualTo("/api/v1/payments")).willReturn(
                aResponse().withHeader("Content-Type", "application/json")
                    .withBody("""{"meta":{"result":"SUCCESS"},"data":{"transactionKey":"tx-1","status":"PENDING"}}"""),
            ),
        )
        requestPaymentUsecase.request(
            RequestPaymentCommand(ctx.loginId, ctx.password, ctx.orderId, CardType.SAMSUNG, "1234-5678-9012-3456"),
        )

        assertThatThrownBy {
            requestPaymentUsecase.request(
                RequestPaymentCommand(ctx.loginId, ctx.password, ctx.orderId, CardType.SAMSUNG, "1234-5678-9012-3456"),
            )
        }.isInstanceOf(CoreException::class.java)
            .satisfies({ assertThat((it as CoreException).errorType).isEqualTo(ErrorType.CONFLICT) })
    }
}
