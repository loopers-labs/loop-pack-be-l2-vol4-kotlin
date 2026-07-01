package com.loopers.interfaces.api

import com.loopers.application.order.CreateOrderCommand
import com.loopers.application.order.CreateOrderItemCommand
import com.loopers.application.product.CreateProductCommand
import com.loopers.application.user.SignupCommand
import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandRepositoryPort
import com.loopers.domain.order.OrderRepositoryPort
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.PaymentRepositoryPort
import com.loopers.domain.payment.PaymentStatus
import com.loopers.interfaces.api.order.OrderApplicationServicePort
import com.loopers.interfaces.api.product.ProductAdminApplicationServicePort
import com.loopers.interfaces.api.user.UserApplicationServicePort
import com.loopers.test.FakePaymentGatewayConfig
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import java.time.LocalDate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FakePaymentGatewayConfig::class)
class PaymentV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val userApplicationService: UserApplicationServicePort,
    private val productAdminApplicationService: ProductAdminApplicationServicePort,
    private val orderApplicationService: OrderApplicationServicePort,
    private val brandRepositoryPort: BrandRepositoryPort,
    private val orderRepositoryPort: OrderRepositoryPort,
    private val paymentRepositoryPort: PaymentRepositoryPort,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private val responseType = object : ParameterizedTypeReference<ApiResponse<Map<String, Any>>>() {}

    private val loginId = "buyer01"
    private val rawPassword = "password1234"

    private fun loginHeaders(): HttpHeaders = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
        set("X-Loopers-LoginId", loginId)
        set("X-Loopers-LoginPw", rawPassword)
    }

    /** 사용자 + 상품 + CREATED 주문을 세팅하고 (orderId, amount) 를 반환한다. */
    private fun setupOrder(): Pair<Long, Long> {
        val userId = userApplicationService.signup(
            SignupCommand(
                loginId = loginId,
                rawPassword = rawPassword,
                name = "테스터",
                birth = LocalDate.of(2000, 1, 1),
                email = "$loginId@example.com",
            ),
        ).id
        val brand = brandRepositoryPort.save(Brand.create(name = "Nike-${System.nanoTime()}", description = "x"))
        val productId = productAdminApplicationService.createProduct(
            CreateProductCommand(name = "p", price = 5_000L, description = "d", brandId = brand.id, quantity = 5),
        ).id
        val order = orderApplicationService.createOrder(
            CreateOrderCommand(userId = userId, items = listOf(CreateOrderItemCommand(productId, 1))),
        )
        return order.id to order.totalAmount
    }

    private fun requestPayment(orderId: Long): ResponseEntity<ApiResponse<Map<String, Any>>> {
        val body = mapOf(
            "orderId" to orderId.toString(),
            "cardType" to "SAMSUNG",
            "cardNo" to "1234-5678-9814-1451",
        )
        return testRestTemplate.exchange(
            "/api/v1/payments",
            HttpMethod.POST,
            HttpEntity(body, loginHeaders()),
            responseType,
        )
    }

    @DisplayName("POST /api/v1/payments")
    @Nested
    inner class RequestPayment {
        @DisplayName("정상 결제 요청 시 transactionKey 를 받고 주문이 결제대기로 전이된다.")
        @Test
        fun returnsTransactionKey_andTransitionsOrderToPaymentPending() {
            val (orderId, amount) = setupOrder()

            val response = requestPayment(orderId)

            val data = response.body?.data
            val transactionKey = data?.get("transactionKey") as? String
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(transactionKey).isNotBlank() },
                { assertThat((data?.get("orderId") as Number).toLong()).isEqualTo(orderId) },
                { assertThat((data?.get("amount") as Number).toLong()).isEqualTo(amount) },
                { assertThat(data?.get("status")).isEqualTo("PENDING") },
                { assertThat(orderRepositoryPort.findById(orderId)?.status).isEqualTo(OrderStatus.PAYMENT_PENDING) },
                {
                    val payment = paymentRepositoryPort.findByTransactionKey(transactionKey!!)
                    assertThat(payment?.status).isEqualTo(PaymentStatus.PENDING)
                    assertThat(payment?.amount).isEqualTo(amount)
                },
            )
        }

        @DisplayName("존재하지 않는 주문으로 결제를 요청하면 404 를 반환한다.")
        @Test
        fun returnsNotFound_whenOrderDoesNotExist() {
            setupOrder()

            val response = requestPayment(orderId = 999_999L)

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }

    @DisplayName("POST /api/v1/payments/callback")
    @Nested
    inner class HandleCallback {
        @DisplayName("SUCCESS 콜백을 받으면 결제가 승인되고 주문이 결제완료로 전이된다.")
        @Test
        fun approvesPayment_andTransitionsOrderToCompleted_onSuccessCallback() {
            val (orderId, amount) = setupOrder()
            val transactionKey = requestPayment(orderId).body?.data?.get("transactionKey") as String

            val callbackBody = mapOf(
                "transactionKey" to transactionKey,
                "orderId" to orderId.toString(),
                "cardType" to "SAMSUNG",
                "cardNo" to "1234-5678-9814-1451",
                "amount" to amount,
                "status" to "SUCCESS",
                "reason" to "정상 승인되었습니다.",
            )
            val response = testRestTemplate.exchange(
                "/api/v1/payments/callback",
                HttpMethod.POST,
                HttpEntity(callbackBody, HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }),
                responseType,
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(paymentRepositoryPort.findByTransactionKey(transactionKey)?.status).isEqualTo(PaymentStatus.SUCCESS) },
                { assertThat(orderRepositoryPort.findById(orderId)?.status).isEqualTo(OrderStatus.PAYMENT_COMPLETED) },
            )
        }
    }
}
