package com.loopers.interfaces.api

import com.loopers.application.order.CreateOrderCommand
import com.loopers.application.order.CreateOrderItemCommand
import com.loopers.application.product.CreateProductCommand
import com.loopers.application.user.SignupCommand
import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandRepositoryPort
import com.loopers.interfaces.api.order.OrderApplicationServicePort
import com.loopers.interfaces.api.product.ProductAdminApplicationServicePort
import com.loopers.interfaces.api.user.UserApplicationServicePort
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
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.time.LocalDate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderAdminV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val userApplicationService: UserApplicationServicePort,
    private val productAdminApplicationService: ProductAdminApplicationServicePort,
    private val orderApplicationService: OrderApplicationServicePort,
    private val brandRepositoryPort: BrandRepositoryPort,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}

    private fun adminHeaders(ldap: String?): HttpHeaders = HttpHeaders().apply {
        ldap?.let { set("X-Loopers-Ldap", it) }
    }

    private fun setupOrder(loginId: String = "buyer01"): Long {
        val userId = userApplicationService.signup(
            SignupCommand(
                loginId = loginId,
                rawPassword = "password1234",
                name = "테스터",
                birth = LocalDate.of(2000, 1, 1),
                email = "$loginId@example.com",
            ),
        ).id
        val brand = brandRepositoryPort.save(Brand.create(name = "Nike-${System.nanoTime()}", description = "x"))
        val productId = productAdminApplicationService.createProduct(
            CreateProductCommand(name = "p", price = 1_000L, description = "d", brandId = brand.id, quantity = 5),
        ).id
        return orderApplicationService.createOrder(
            CreateOrderCommand(userId = userId, items = listOf(CreateOrderItemCommand(productId, 1))),
        ).id
    }

    private fun getOrders(ldap: String?): ResponseEntity<ApiResponse<Any>> = testRestTemplate.exchange(
        "/api-admin/v1/orders?page=0&size=20",
        HttpMethod.GET,
        HttpEntity<Any>(adminHeaders(ldap)),
        responseType,
    )

    private fun getOrder(orderId: Long, ldap: String?): ResponseEntity<ApiResponse<Any>> = testRestTemplate.exchange(
        "/api-admin/v1/orders/$orderId",
        HttpMethod.GET,
        HttpEntity<Any>(adminHeaders(ldap)),
        responseType,
    )

    @DisplayName("GET /api-admin/v1/orders")
    @Nested
    inner class AdminGetOrders {
        @DisplayName("정상 어드민 헤더로 호출하면 loginId가 포함된 목록을 반환한다.")
        @Test
        fun returnsAdminListWithLoginId() {
            setupOrder(loginId = "buyer01")

            val response = getOrders(ldap = "loopers.admin")

            val data = response.body?.data as? Map<*, *>
            val items = data?.get("items") as? List<*>
            val first = items?.firstOrNull() as? Map<*, *>
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(items).hasSize(1) },
                { assertThat(first?.get("loginId")).isEqualTo("buyer01") },
            )
        }

        @DisplayName("어드민 헤더가 누락되면 403 응답을 받는다.")
        @Test
        fun returnsForbidden_whenLdapMissing() {
            setupOrder()

            val response = getOrders(ldap = null)

            assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        }
    }

    @DisplayName("GET /api-admin/v1/orders/{id}")
    @Nested
    inner class AdminGetOrder {
        @DisplayName("정상 어드민 헤더로 상세를 조회하면 loginId 가 포함된 상세를 반환한다.")
        @Test
        fun returnsAdminDetail() {
            val orderId = setupOrder(loginId = "buyer02")

            val response = getOrder(orderId, ldap = "loopers.admin")

            val data = response.body?.data as? Map<*, *>
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(data?.get("loginId")).isEqualTo("buyer02") },
                { assertThat((data?.get("id") as? Number)?.toLong()).isEqualTo(orderId) },
            )
        }

        @DisplayName("존재하지 않는 orderId 로 조회하면 404 응답을 받는다.")
        @Test
        fun returnsNotFound_whenMissing() {
            val response = getOrder(9999L, ldap = "loopers.admin")

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }
}
