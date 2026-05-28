package com.loopers.interfaces.api

import com.loopers.application.product.CreateProductCommand
import com.loopers.application.user.SignupCommand
import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandRepositoryPort
import com.loopers.interfaces.api.product.ProductAdminApplicationServicePort
import com.loopers.interfaces.api.user.UserApplicationServicePort
import com.loopers.test.FakePaymentGateway
import com.loopers.test.FakePaymentGatewayConfig
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
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
import java.net.URI
import java.time.LocalDate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FakePaymentGatewayConfig::class)
class OrderV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val userApplicationService: UserApplicationServicePort,
    private val productAdminApplicationService: ProductAdminApplicationServicePort,
    private val brandRepositoryPort: BrandRepositoryPort,
    private val fakePaymentGateway: FakePaymentGateway,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @BeforeEach
    fun setUp() {
        fakePaymentGateway.reset()
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun signup(loginId: String = "tester01", pw: String = "password1234"): Long =
        userApplicationService.signup(
            SignupCommand(
                loginId = loginId,
                rawPassword = pw,
                name = "테스터",
                birth = LocalDate.of(2000, 1, 1),
                email = "$loginId@example.com",
            ),
        ).id

    private fun saveBrand(name: String = "Nike-${System.nanoTime()}"): Long =
        brandRepositoryPort.save(Brand.create(name = name, description = "x")).id

    private fun saveProduct(brandId: Long, quantity: Int = 10, price: Long = 1_000L): Long =
        productAdminApplicationService.createProduct(
            CreateProductCommand(name = "p", price = price, description = "d", brandId = brandId, quantity = quantity),
        ).id

    private fun authHeaders(loginId: String?, loginPw: String?): HttpHeaders = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
        loginId?.let { set("X-Loopers-LoginId", it) }
        loginPw?.let { set("X-Loopers-LoginPw", it) }
    }

    private val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}

    private fun postOrder(body: Map<String, Any>, loginId: String?, loginPw: String?): ResponseEntity<ApiResponse<Any>> =
        testRestTemplate.exchange(
            "/api/v1/orders",
            HttpMethod.POST,
            HttpEntity(body, authHeaders(loginId, loginPw)),
            responseType,
        )

    private fun getMyOrder(orderId: Long, loginId: String?, loginPw: String?): ResponseEntity<ApiResponse<Any>> =
        testRestTemplate.exchange(
            "/api/v1/orders/$orderId",
            HttpMethod.GET,
            HttpEntity<Any>(authHeaders(loginId, loginPw)),
            responseType,
        )

    private fun encode(value: String): String = value.replace("+", "%2B")

    private fun getMyOrders(
        startAt: String,
        endAt: String,
        loginId: String?,
        loginPw: String?,
    ): ResponseEntity<ApiResponse<Any>> = testRestTemplate.exchange(
        URI.create("/api/v1/orders?startAt=${encode(startAt)}&endAt=${encode(endAt)}&page=0&size=20"),
        HttpMethod.GET,
        HttpEntity<Any>(authHeaders(loginId, loginPw)),
        responseType,
    )

    @DisplayName("POST /api/v1/orders")
    @Nested
    inner class CreateOrder {
        @DisplayName("정상 주문 시 200 응답이고 status=PAYMENT_COMPLETED 가 반환된다.")
        @Test
        fun returnsSuccess_whenValid() {
            signup()
            val productId = saveProduct(saveBrand())

            val response = postOrder(
                body = mapOf("items" to listOf(mapOf("productId" to productId, "quantity" to 2))),
                loginId = "tester01",
                loginPw = "password1234",
            )

            val data = response.body?.data as? Map<*, *>
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(data?.get("status") as? String).isEqualTo("PAYMENT_COMPLETED") },
                { assertThat((data?.get("totalAmount") as? Number)?.toLong()).isEqualTo(2_000L) },
            )
        }

        @DisplayName("잘못된 비밀번호면 401 응답을 받는다.")
        @Test
        fun returnsUnauthorized_whenLoginFails() {
            signup()
            val productId = saveProduct(saveBrand())

            val response = postOrder(
                body = mapOf("items" to listOf(mapOf("productId" to productId, "quantity" to 1))),
                loginId = "tester01",
                loginPw = "wrong-password",
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @DisplayName("없는 상품 ID로 주문하면 404 응답을 받는다.")
        @Test
        fun returnsNotFound_whenProductMissing() {
            signup()

            val response = postOrder(
                body = mapOf("items" to listOf(mapOf("productId" to 9999, "quantity" to 1))),
                loginId = "tester01",
                loginPw = "password1234",
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("items가 비어 있으면 400 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenEmptyItems() {
            signup()

            val response = postOrder(
                body = mapOf("items" to emptyList<Any>()),
                loginId = "tester01",
                loginPw = "password1234",
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }

    @DisplayName("GET /api/v1/orders")
    @Nested
    inner class GetMyOrders {
        @DisplayName("본인 주문 목록을 페이지 형태로 반환한다.")
        @Test
        fun returnsOwnOrders() {
            signup()
            val productId = saveProduct(saveBrand())
            postOrder(
                body = mapOf("items" to listOf(mapOf("productId" to productId, "quantity" to 1))),
                loginId = "tester01",
                loginPw = "password1234",
            )

            val response = getMyOrders(
                startAt = "2024-01-01T00:00:00+09:00",
                endAt = "2099-12-31T23:59:59+09:00",
                loginId = "tester01",
                loginPw = "password1234",
            )

            val data = response.body?.data as? Map<*, *>
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat((data?.get("totalElements") as? Number)?.toLong()).isEqualTo(1L) },
                { assertThat((data?.get("items") as? List<*>)).hasSize(1) },
            )
        }

        @DisplayName("startAt > endAt 이면 400 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenStartAfterEnd() {
            signup()

            val response = getMyOrders(
                startAt = "2099-12-31T23:59:59+09:00",
                endAt = "2024-01-01T00:00:00+09:00",
                loginId = "tester01",
                loginPw = "password1234",
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("주문이 없으면 빈 items로 200 응답을 받는다.")
        @Test
        fun returnsEmpty_whenNoOrders() {
            signup()

            val response = getMyOrders(
                startAt = "2024-01-01T00:00:00+09:00",
                endAt = "2099-12-31T23:59:59+09:00",
                loginId = "tester01",
                loginPw = "password1234",
            )

            assertThat(response.statusCode)
                .withFailMessage { "body=${response.body}" }
                .isEqualTo(HttpStatus.OK)
            val data = response.body?.data as? Map<*, *>
            assertAll(
                { assertThat((data?.get("items") as? List<*>)).isEmpty() },
                { assertThat((data?.get("totalElements") as? Number)?.toLong()).isEqualTo(0L) },
            )
        }
    }

    @DisplayName("GET /api/v1/orders/{id}")
    @Nested
    inner class GetMyOrder {
        @DisplayName("본인의 주문 상세를 반환한다.")
        @Test
        fun returnsOwnOrder() {
            signup()
            val productId = saveProduct(saveBrand())
            val createRes = postOrder(
                body = mapOf("items" to listOf(mapOf("productId" to productId, "quantity" to 1))),
                loginId = "tester01",
                loginPw = "password1234",
            )
            val createdId = ((createRes.body?.data as Map<*, *>)["id"] as Number).toLong()

            val response = getMyOrder(createdId, "tester01", "password1234")

            val data = response.body?.data as? Map<*, *>
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat((data?.get("id") as? Number)?.toLong()).isEqualTo(createdId) },
                { assertThat((data?.get("items") as? List<*>)).hasSize(1) },
            )
        }

        @DisplayName("타인의 주문을 조회하면 403 응답을 받는다.")
        @Test
        fun returnsForbidden_whenOtherUser() {
            signup(loginId = "owner01")
            signup(loginId = "stranger01")
            val productId = saveProduct(saveBrand())
            val createRes = postOrder(
                body = mapOf("items" to listOf(mapOf("productId" to productId, "quantity" to 1))),
                loginId = "owner01",
                loginPw = "password1234",
            )
            val createdId = ((createRes.body?.data as Map<*, *>)["id"] as Number).toLong()

            val response = getMyOrder(createdId, "stranger01", "password1234")

            assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        }

        @DisplayName("존재하지 않는 orderId로 조회하면 404 응답을 받는다.")
        @Test
        fun returnsNotFound_whenMissing() {
            signup()

            val response = getMyOrder(9999L, "tester01", "password1234")

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }
}
