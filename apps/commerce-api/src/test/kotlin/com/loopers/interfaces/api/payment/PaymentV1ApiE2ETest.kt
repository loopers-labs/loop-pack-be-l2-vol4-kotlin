package com.loopers.interfaces.api.payment

import com.loopers.application.user.UserFacade
import com.loopers.domain.brand.BrandService
import com.loopers.domain.order.OrderService
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.PgClient
import com.loopers.domain.payment.PgTransactionResult
import com.loopers.domain.payment.PgTransactionStatus
import com.loopers.domain.payment.PgUnavailableException
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.ProductStatus
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.order.OrderV1Dto
import com.loopers.utils.DatabaseCleanUp
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import java.time.LocalDate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentV1ApiE2ETest {
    @Autowired
    lateinit var testRestTemplate: TestRestTemplate

    @Autowired
    lateinit var userFacade: UserFacade

    @Autowired
    lateinit var brandService: BrandService

    @Autowired
    lateinit var productService: ProductService

    @Autowired
    lateinit var orderService: OrderService

    @Autowired
    lateinit var paymentService: PaymentService

    @Autowired
    lateinit var databaseCleanUp: DatabaseCleanUp

    @MockkBean
    lateinit var pgClient: PgClient

    private val loginId = "user123"
    private val rawPassword = "Valid1!pw"
    private val cardNo = "1234-5678-9814-1451"

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun signUp() {
        userFacade.signUp(loginId, rawPassword, "홍길동", LocalDate.of(1994, 7, 14), "hong@example.com")
    }

    private fun authHeaders() = HttpHeaders().apply {
        set("X-Loopers-LoginId", loginId)
        set("X-Loopers-LoginPw", rawPassword)
    }

    /** 주문을 하나 생성하고 orderId 를 돌려준다. */
    private fun placePendingOrder(price: Long = 5_000, quantity: Int = 1): Long {
        val brand = brandService.register("Nike")
        val product = productService.register(brand.id, "Air Max", price, 10, ProductStatus.ON_SALE)
        val request = OrderV1Dto.PlaceOrderRequest(
            items = listOf(OrderV1Dto.OrderLineRequest(productId = product.id, quantity = quantity)),
        )
        val type = object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {}
        val response =
            testRestTemplate.exchange("/api/v1/orders", HttpMethod.POST, HttpEntity(request, authHeaders()), type)
        return response.body!!.data!!.id
    }

    private fun pay(orderId: Long): ApiResponse<PaymentV1Dto.PaymentResponse>? {
        val request = PaymentV1Dto.PayRequest(orderId.toString(), CardType.SAMSUNG, cardNo)
        val type = object : ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PaymentResponse>>() {}
        return testRestTemplate
            .exchange("/api/v1/payments", HttpMethod.POST, HttpEntity(request, authHeaders()), type)
            .body
    }

    @DisplayName("POST /api/v1/payments")
    @Nested
    inner class Pay {
        @DisplayName("정상 요청이면, PG 접수 후 결제는 PENDING + 거래키가 부여된 상태로 응답한다.")
        @Test
        fun acceptsAndAssignsTransactionKey() {
            // arrange
            signUp()
            val orderId = placePendingOrder()
            every { pgClient.requestPayment(any()) } returns
                PgTransactionResult("TR-1", null, PgTransactionStatus.PENDING, null)

            // act
            val body = pay(orderId)

            // assert
            val saved = paymentService.getByOrderId(orderId)
            assertAll(
                { assertThat(body?.data?.status).isEqualTo(PaymentStatus.PENDING) },
                { assertThat(body?.data?.transactionKey).isEqualTo("TR-1") },
                { assertThat(saved.transactionKey).isEqualTo("TR-1") },
                { assertThat(saved.amount).isEqualTo(5_000L) },
            )
        }

        @DisplayName("PG 접수가 불가하면(장애), 예외를 전파하지 않고 결제는 PENDING 으로 보호된다.")
        @Test
        fun keepsPending_whenPgUnavailable() {
            // arrange
            signUp()
            val orderId = placePendingOrder()
            every { pgClient.requestPayment(any()) } throws PgUnavailableException()

            // act
            val body = pay(orderId)

            // assert
            val saved = paymentService.getByOrderId(orderId)
            assertAll(
                { assertThat(body?.data?.status).isEqualTo(PaymentStatus.PENDING) },
                { assertThat(body?.data?.transactionKey).isNull() },
                { assertThat(saved.status).isEqualTo(PaymentStatus.PENDING) },
            )
        }
    }

    @DisplayName("POST /api/v1/payments/callback")
    @Nested
    inner class Callback {
        @DisplayName("SUCCESS 콜백을 받으면, 결제는 SUCCESS 로 확정되고 주문은 PAID 로 전이된다.")
        @Test
        fun success_settlesPaymentAndPaysOrder() {
            // arrange
            signUp()
            val orderId = placePendingOrder()
            every { pgClient.requestPayment(any()) } returns
                PgTransactionResult("TR-9", null, PgTransactionStatus.PENDING, null)
            pay(orderId)

            val callback = PaymentV1Dto.CallbackRequest("TR-9", orderId.toString(), PgTransactionStatus.SUCCESS, null)

            // act
            val type = object : ParameterizedTypeReference<ApiResponse<Any>>() {}
            testRestTemplate.exchange("/api/v1/payments/callback", HttpMethod.POST, HttpEntity(callback), type)

            // assert
            assertAll(
                { assertThat(paymentService.getByOrderId(orderId).status).isEqualTo(PaymentStatus.SUCCESS) },
                { assertThat(orderService.getById(orderId).status).isEqualTo(OrderStatus.PAID) },
            )
        }

        @DisplayName("동일한 SUCCESS 콜백이 중복 수신되어도, 멱등하게 PAID 상태를 유지한다.")
        @Test
        fun success_isIdempotentOnDuplicate() {
            // arrange
            signUp()
            val orderId = placePendingOrder()
            every { pgClient.requestPayment(any()) } returns
                PgTransactionResult("TR-10", null, PgTransactionStatus.PENDING, null)
            pay(orderId)
            val callback = PaymentV1Dto.CallbackRequest("TR-10", orderId.toString(), PgTransactionStatus.SUCCESS, null)
            val type = object : ParameterizedTypeReference<ApiResponse<Any>>() {}

            // act
            testRestTemplate.exchange("/api/v1/payments/callback", HttpMethod.POST, HttpEntity(callback), type)
            val second =
                testRestTemplate.exchange("/api/v1/payments/callback", HttpMethod.POST, HttpEntity(callback), type)

            // assert
            assertAll(
                { assertThat(second.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(orderService.getById(orderId).status).isEqualTo(OrderStatus.PAID) },
            )
        }
    }

    @DisplayName("POST /api/v1/payments/sync")
    @Nested
    inner class Sync {
        @DisplayName("콜백이 오지 않은 PENDING 건을 PG 조회로 동기화하면, SUCCESS 로 복구되고 주문이 PAID 된다.")
        @Test
        fun recoversFromPgQuery() {
            // arrange
            signUp()
            val orderId = placePendingOrder()
            every { pgClient.requestPayment(any()) } returns
                PgTransactionResult("TR-11", null, PgTransactionStatus.PENDING, null)
            pay(orderId)
            // 콜백은 오지 않고, PG 조회 시 SUCCESS 로 확인된다.
            every { pgClient.getTransaction("1", "TR-11") } returns
                PgTransactionResult("TR-11", orderId.toString(), PgTransactionStatus.SUCCESS, null)

            // act
            val type = object : ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PaymentResponse>>() {}
            val body = testRestTemplate.exchange(
                "/api/v1/payments/sync?orderId=$orderId",
                HttpMethod.POST,
                HttpEntity<Void>(authHeaders()),
                type,
            ).body

            // assert
            assertAll(
                { assertThat(body?.data?.status).isEqualTo(PaymentStatus.SUCCESS) },
                { assertThat(orderService.getById(orderId).status).isEqualTo(OrderStatus.PAID) },
            )
        }
    }
}
