package com.loopers.application.payment

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.loopers.application.payment.usecase.RequestPaymentUsecase
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PaymentRepository
import com.loopers.support.runConcurrently
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.test.context.TestPropertySource

@SpringBootTest
@AutoConfigureWireMock(port = 0)
@TestPropertySource(properties = ["pg.base-url=http://localhost:\${wiremock.server.port}"])
class RequestPaymentConcurrencyTest @Autowired constructor(
    private val requestPaymentUsecase: RequestPaymentUsecase,
    private val paymentRepository: PaymentRepository,
    private val fixtures: PaymentTestFixtures,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() = databaseCleanUp.truncateAllTables()

    @Test
    fun `동일 주문에 대한 동시 결제 요청은 정확히 하나만 성공하고 나머지는 CONFLICT로 실패한다`() {
        val ctx = fixtures.pendingOrder()
        val threadCount = 5

        stubFor(
            post(urlPathEqualTo("/api/v1/payments")).willReturn(
                aResponse().withHeader("Content-Type", "application/json")
                    .withBody("""{"meta":{"result":"SUCCESS"},"data":{"transactionKey":"tx-concurrent","status":"PENDING"}}"""),
            ),
        )

        val errors = runConcurrently(threadCount) {
            requestPaymentUsecase.request(
                RequestPaymentCommand(ctx.loginId, ctx.password, ctx.orderId, CardType.SAMSUNG, "1234-5678-9012-3456"),
            )
        }

        val successCount = threadCount - errors.size
        assertThat(successCount).isEqualTo(1)

        assertThat(errors).allSatisfy { error ->
            assertThat(error).isInstanceOf(CoreException::class.java)
            assertThat((error as CoreException).errorType).isEqualTo(ErrorType.CONFLICT)
        }

        // DB에 정확히 한 행만 존재 — NonUniqueResultException 없이 조회 가능해야 한다
        assertThat(paymentRepository.findByOrderId(ctx.orderId)).isNotNull
    }
}
