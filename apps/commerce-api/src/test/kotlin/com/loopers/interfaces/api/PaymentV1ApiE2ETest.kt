package com.loopers.interfaces.api

import com.loopers.application.payment.PaymentCommand
import com.loopers.application.payment.PaymentGateway
import com.loopers.application.payment.PaymentResult
import com.loopers.application.payment.PaymentStatus
import com.loopers.application.payment.PaymentTransactionInfo
import com.loopers.domain.payment.PaymentRepository
import com.loopers.infrastructure.product.ProductJpaEntity
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.infrastructure.stock.StockJpaEntity
import com.loopers.infrastructure.stock.StockJpaRepository
import com.loopers.interfaces.api.user.UserV1Dto
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val paymentRepository: PaymentRepository,
    private val fakePaymentGateway: FakePaymentGateway,
    private val productJpaRepository: ProductJpaRepository,
    private val stockJpaRepository: StockJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val PAYMENT_ENDPOINT = "/api/v1/payments"
        private const val ORDER_ENDPOINT = "/api/v1/orders"
        private const val USER_ENDPOINT = "/api/v1/users"
        private const val LOGIN_ID = "seondays"
        private const val PASSWORD = "Password1!"
    }

    @AfterEach
    fun tearDown() {
        fakePaymentGateway.reset()
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("POST /api/v1/payments")
    @Nested
    inner class RequestPayment {
        @DisplayName("인증된 사용자가 결제 요청하면 PENDING 상태의 Payment를 반환한다.")
        @Test
        fun requestPayment_returnsPendingPayment() {
            // arrange
            val headers = signUpAndGetAuthHeaders()
            val product = saveProductWithStock(price = 10_000L, stock = 10)
            val orderId = placeOrder(headers, product.id)
            fakePaymentGateway.nextStatus = PaymentStatus.PENDING

            val request = mapOf(
                "orderId" to orderId,
                "cardType" to "SAMSUNG",
                "cardNo" to "1234-5678-9012-3456",
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Map<String, Any?>>>() {}
            val response = testRestTemplate.exchange(
                PAYMENT_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(request, headers),
                responseType,
            )

            // assert
            val data = response.body?.data!!
            val savedPayment = paymentRepository.findByOrderId(orderId.toLong())
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED) },
                { assertThat(data["status"]).isEqualTo("PENDING") },
                { assertThat(data["transactionKey"]).isNotNull() },
                { assertThat(data["orderId"]).isEqualTo(orderId) },
                { assertThat(savedPayment).isNotNull() },
                { assertThat(savedPayment!!.status).isEqualTo(PaymentStatus.PENDING) },
            )
        }

        @DisplayName("인증 헤더가 없으면 401을 반환한다.")
        @Test
        fun requestPayment_returnsUnauthorized_whenNoAuthHeaders() {
            // arrange
            val request = mapOf(
                "orderId" to 1,
                "cardType" to "SAMSUNG",
                "cardNo" to "1234-5678-9012-3456",
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}
            val response = testRestTemplate.exchange(
                PAYMENT_ENDPOINT,
                HttpMethod.POST,
                jsonEntity(request),
                responseType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @DisplayName("존재하지 않는 주문에 대해 결제 요청하면 404를 반환한다.")
        @Test
        fun requestPayment_returnsNotFound_whenOrderDoesNotExist() {
            // arrange
            val headers = signUpAndGetAuthHeaders()
            fakePaymentGateway.nextStatus = PaymentStatus.PENDING

            val request = mapOf(
                "orderId" to 999,
                "cardType" to "SAMSUNG",
                "cardNo" to "1234-5678-9012-3456",
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}
            val response = testRestTemplate.exchange(
                PAYMENT_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(request, headers),
                responseType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }

    private fun signUpAndGetAuthHeaders(): HttpHeaders {
        val signUpRequest = UserV1Dto.SignUpRequest(
            loginId = LOGIN_ID,
            password = PASSWORD,
            name = "선데이",
            birthDate = java.time.LocalDate.of(1990, 1, 1),
            email = "seondays@example.com",
        )
        testRestTemplate.exchange(
            USER_ENDPOINT,
            HttpMethod.POST,
            jsonEntity(signUpRequest),
            object : ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>>() {},
        )
        return HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-Loopers-LoginId", LOGIN_ID)
            set("X-Loopers-LoginPw", PASSWORD)
        }
    }

    private fun placeOrder(headers: HttpHeaders, productId: Long): Int {
        val orderRequest = mapOf(
            "items" to listOf(mapOf("productId" to productId, "quantity" to 1)),
        )
        val responseType = object : ParameterizedTypeReference<ApiResponse<Map<String, Any?>>>() {}
        val response = testRestTemplate.exchange(
            ORDER_ENDPOINT,
            HttpMethod.POST,
            HttpEntity(orderRequest, headers),
            responseType,
        )
        return (response.body?.data?.get("id") as Number).toInt()
    }

    private fun saveProductWithStock(
        brandId: Long = 1L,
        name: String = "Loopers T-Shirt",
        description: String = "매일 입기 좋은 티셔츠",
        price: Long = 10_000L,
        stock: Int = 10,
    ): ProductJpaEntity {
        val product = productJpaRepository.save(
            ProductJpaEntity(
                brandId = brandId,
                name = name,
                description = description,
                price = price,
            ),
        )
        stockJpaRepository.save(StockJpaEntity(productId = product.id, quantity = stock))
        return product
    }

    private fun <T> jsonEntity(body: T): HttpEntity<T> {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        return HttpEntity(body, headers)
    }

    @TestConfiguration
    class PaymentTestConfiguration {
        @Bean
        @Primary
        fun fakePaymentGateway(): FakePaymentGateway {
            return FakePaymentGateway()
        }
    }

    class FakePaymentGateway : PaymentGateway {
        var nextStatus: PaymentStatus = PaymentStatus.PENDING
        var lastCommand: PaymentCommand? = null
        override fun pay(command: PaymentCommand): PaymentResult {
            lastCommand = command
            return PaymentResult(
                transactionKey = "fake:TR:${System.currentTimeMillis()}",
                status = nextStatus,
                reason = null,
            )
        }

        override fun getTransactionStatus(transactionKey: String): PaymentTransactionInfo {
            throw UnsupportedOperationException()
        }

        override fun getTransactionsByOrderId(orderId: String): List<PaymentTransactionInfo> {
            return emptyList()
        }

        fun reset() {
            nextStatus = PaymentStatus.PENDING
            lastCommand = null
        }
    }
}
