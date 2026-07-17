package com.loopers.interfaces.api

import com.loopers.application.payment.PaymentCommand
import com.loopers.application.payment.PaymentGateway
import com.loopers.application.payment.PaymentResult
import com.loopers.application.payment.PaymentStatus
import com.loopers.application.payment.PaymentTransactionInfo
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.queue.EntryToken
import com.loopers.domain.queue.EntryTokenRepository
import com.loopers.infrastructure.product.ProductJpaEntity
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.infrastructure.stock.StockJpaEntity
import com.loopers.infrastructure.stock.StockJpaRepository
import com.loopers.interfaces.api.user.UserV1Dto
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
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
    private val entryTokenRepository: EntryTokenRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    companion object {
        private const val PAYMENT_ENDPOINT = "/api/v1/payments"
        private const val ORDER_ENDPOINT = "/api/v1/orders"
        private const val USER_ENDPOINT = "/api/v1/users"
        private const val LOGIN_ID = "seondays"
        private const val PASSWORD = "Password1!"
        private const val QUEUE_TOKEN = "test-queue-token"
    }

    @AfterEach
    fun tearDown() {
        fakePaymentGateway.reset()
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @DisplayName("POST /api/v1/payments")
    @Nested
    inner class RequestPayment {
        @DisplayName("인증된 사용자가 결제를 요청하면 REQUESTED 상태의 Payment를 생성한다.")
        @Test
        fun requestPayment_createsRequestedPayment() {
            // arrange
            val headers = signUpAndGetAuthHeaders()
            val product = saveProductWithStock(price = 10_000L, stock = 10)
            val orderId = placeOrder(headers, product.id)

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

            // assert : 결제 요청은 비동기 처리이므로 접수 직후에는 REQUESTED 상태이다.
            // (REQUESTED -> PENDING 전이는 PaymentRequestProcessorTest 가 검증한다.)
            val data = response.body?.data!!
            val savedPayment = paymentRepository.findByOrderId(orderId.toLong())
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED) },
                { assertThat(data["status"]).isEqualTo("REQUESTED") },
                { assertThat(data["orderId"]).isEqualTo(orderId) },
                { assertThat(savedPayment).isNotNull() },
                { assertThat(savedPayment!!.status).isEqualTo(PaymentStatus.REQUESTED) },
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
        val signUpResponse = testRestTemplate.exchange(
            USER_ENDPOINT,
            HttpMethod.POST,
            jsonEntity(signUpRequest),
            object : ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>>() {},
        )
        val userId = signUpResponse.body?.data?.id
            ?: error("회원 가입 응답에 id가 없습니다.")
        // 주문 API 가 대기열 입장 토큰을 요구하므로, 테스트에서는 토큰을 직접 발급해 둔다.
        entryTokenRepository.save(userId, EntryToken(QUEUE_TOKEN))

        return HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-Loopers-LoginId", LOGIN_ID)
            set("X-Loopers-LoginPw", PASSWORD)
            set("X-Loopers-QueueToken", QUEUE_TOKEN)
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
