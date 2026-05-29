package com.loopers.interfaces.api.order

import com.loopers.domain.catalog.ProductStock
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.user.PasswordEncoder
import com.loopers.domain.user.RawPassword
import com.loopers.domain.user.User
import com.loopers.domain.user.UserRole
import com.loopers.infrastructure.catalog.ProductStockJpaRepository
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.interfaces.api.ApiResponse
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
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
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val productStockJpaRepository: ProductStockJpaRepository,
    private val userJpaRepository: UserJpaRepository,
    private val passwordEncoder: PasswordEncoder,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Test
    fun checkoutReturnsPaymentPendingOrder() {
        saveUser()
        productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 5))
        val body = mapOf(
            "items" to listOf(
                mapOf(
                    "productId" to 10L,
                    "productNameSnapshot" to "상품A",
                    "brandNameSnapshot" to "브랜드A",
                    "priceSnapshot" to 1000L,
                    "quantity" to 2,
                ),
            ),
            "deliveryAddress" to "서울시 강남구",
            "deliveryRequest" to "문 앞",
            "phoneNumber" to "010-1234-5678",
            "reservationExpiresAt" to LocalDateTime.now().plusMinutes(10).toString(),
        )
        val responseType = object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {}

        val response = testRestTemplate.exchange(
            "/api/v1/orders/checkout",
            HttpMethod.POST,
            HttpEntity(body, authHeaders()),
            responseType,
        )

        assertAll(
            { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(response.body?.data?.status).isEqualTo(OrderStatus.PAYMENT_PENDING) },
            { assertThat(response.body?.data?.items).hasSize(1) },
        )
    }

    private fun saveUser() {
        userJpaRepository.save(
            User(
                loginId = "loopers01",
                encryptedPassword = passwordEncoder.encode(RawPassword("abcd1234")),
                name = "홍길동",
                birthdate = LocalDate.of(1990, 1, 1),
                email = "user@example.com",
                role = UserRole.CONSUMER,
            ),
        )
    }

    private fun authHeaders(): HttpHeaders = HttpHeaders().apply {
        add("X-Loopers-LoginId", "loopers01")
        add("X-Loopers-LoginPw", "abcd1234")
    }
}
