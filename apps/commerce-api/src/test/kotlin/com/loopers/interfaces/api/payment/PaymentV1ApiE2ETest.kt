package com.loopers.interfaces.api.payment

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.loopers.application.payment.PaymentTestFixtures
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderStatus
import com.loopers.interfaces.api.ApiResponse
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.test.context.TestPropertySource

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWireMock(port = 0)
@TestPropertySource(properties = ["pg.base-url=http://localhost:\${wiremock.server.port}"])
class PaymentV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val orderRepository: OrderRepository,
    private val fixtures: PaymentTestFixtures,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() = databaseCleanUp.truncateAllTables()

    @Test
    fun `결제 요청 후 성공 콜백을 받으면 주문이 PAID 가 된다`() {
        val ctx = fixtures.pendingOrder()
        stubFor(
            post(urlPathEqualTo("/api/v1/payments")).willReturn(
                aResponse().withHeader("Content-Type", "application/json")
                    .withBody("""{"meta":{"result":"SUCCESS"},"data":{"transactionKey":"tx-1","status":"PENDING"}}"""),
            ),
        )
        val headers = HttpHeaders().apply {
            set("X-Loopers-LoginId", ctx.loginId)
            set("X-Loopers-LoginPw", ctx.password)
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
        }

        // 1) 결제 요청
        val reqBody = """{"orderId":${ctx.orderId},"cardType":"SAMSUNG","cardNo":"1234-5678-9012-3456"}"""
        val reqResp = testRestTemplate.exchange(
            "/api/v1/payments",
            HttpMethod.POST,
            HttpEntity(reqBody, headers),
            object : ParameterizedTypeReference<ApiResponse<Any>>() {},
        )
        assertThat(reqResp.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS)

        // 2) PG 콜백(성공)
        val cbBody = """{"transactionKey":"tx-1","orderId":${ctx.orderId},"status":"SUCCESS"}"""
        val cbHeaders = HttpHeaders().apply { contentType = org.springframework.http.MediaType.APPLICATION_JSON }
        testRestTemplate.exchange(
            "/api/v1/payments/callback",
            HttpMethod.POST,
            HttpEntity(cbBody, cbHeaders),
            object : ParameterizedTypeReference<ApiResponse<Any>>() {},
        )

        // 3) 주문 상태 확인
        assertThat(orderRepository.findById(ctx.orderId)?.status).isEqualTo(OrderStatus.PAID)
    }
}
