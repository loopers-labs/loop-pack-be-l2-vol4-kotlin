package com.loopers.interfaces.api.order

import com.loopers.domain.catalog.Brand
import com.loopers.domain.catalog.Product
import com.loopers.domain.catalog.ProductStock
import com.loopers.domain.catalog.ProductStats
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.user.PasswordEncoder
import com.loopers.domain.user.RawPassword
import com.loopers.domain.user.User
import com.loopers.domain.user.UserRole
import com.loopers.infrastructure.catalog.BrandJpaRepository
import com.loopers.infrastructure.catalog.ProductJpaRepository
import com.loopers.infrastructure.catalog.ProductStatsJpaRepository
import com.loopers.infrastructure.catalog.ProductStockJpaRepository
import com.loopers.infrastructure.order.OrderJpaRepository
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.catalog.CatalogV1Dto
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
    private val brandJpaRepository: BrandJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val productStatsJpaRepository: ProductStatsJpaRepository,
    private val productStockJpaRepository: ProductStockJpaRepository,
    private val orderJpaRepository: OrderJpaRepository,
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
        saveUser("loopers01")
        val product = saveCatalogProduct("Nike", "Air Max", 1000L, stockQuantity = 5)
        val body = mapOf(
            "items" to listOf(
                mapOf(
                    "productId" to product.productId,
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
            { assertThat(response.body?.data?.couponId).isNull() },
            { assertThat(response.body?.data?.totalAmount).isEqualTo(2000L) },
            { assertThat(response.body?.data?.discountAmount).isZero() },
            { assertThat(response.body?.data?.paymentAmount).isEqualTo(2000L) },
            { assertThat(response.body?.data?.items).hasSize(1) },
            { assertThat(response.body?.data?.items?.single()?.productNameSnapshot).isEqualTo("Air Max") },
            { assertThat(response.body?.data?.items?.single()?.brandNameSnapshot).isEqualTo("Nike") },
            { assertThat(response.body?.data?.items?.single()?.priceSnapshot).isEqualTo(1000L) },
        )
    }

    @Test
    fun otherUserCannotPayOrCancelOrder() {
        saveUser("owner01")
        saveUser("other01")
        val product = saveCatalogProduct("Nike", "Air Max", 1000L, stockQuantity = 5)
        val paymentTarget = checkout("owner01", product.productId).body?.data!!
        val cancelTarget = checkout("owner01", product.productId).body?.data!!
        val responseType = object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {}

        val paymentByOther = testRestTemplate.exchange(
            "/api/v1/orders/${paymentTarget.orderId}/payment",
            HttpMethod.POST,
            HttpEntity(
                mapOf(
                    "cardType" to "SAMSUNG",
                    "cardNo" to "1234-5678-1234-5678",
                ),
                authHeaders("other01"),
            ),
            responseType,
        )
        val cancelByOther = testRestTemplate.exchange(
            "/api/v1/orders/${cancelTarget.orderId}/cancel",
            HttpMethod.POST,
            HttpEntity<Any>(authHeaders("other01")),
            responseType,
        )

        assertAll(
            { assertThat(paymentByOther.statusCode).isEqualTo(HttpStatus.FORBIDDEN) },
            { assertThat(cancelByOther.statusCode).isEqualTo(HttpStatus.FORBIDDEN) },
            { assertThat(orderJpaRepository.findById(paymentTarget.orderId).orElseThrow().status).isEqualTo(OrderStatus.PAYMENT_PENDING) },
            { assertThat(orderJpaRepository.findById(cancelTarget.orderId).orElseThrow().status).isEqualTo(OrderStatus.PAYMENT_PENDING) },
        )
    }

    private fun checkout(loginId: String, productId: Long) =
        testRestTemplate.exchange(
            "/api/v1/orders/checkout",
            HttpMethod.POST,
            HttpEntity(
                mapOf(
                    "items" to listOf(
                        mapOf(
                            "productId" to productId,
                            "quantity" to 1,
                        ),
                    ),
                    "deliveryAddress" to "서울시 강남구",
                    "deliveryRequest" to "문 앞",
                    "phoneNumber" to "010-1234-5678",
                    "reservationExpiresAt" to LocalDateTime.now().plusMinutes(10).toString(),
                ),
                authHeaders(loginId),
            ),
            object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {},
        )

    private fun saveCatalogProduct(
        brandName: String,
        productName: String,
        price: Long,
        stockQuantity: Int,
    ): CatalogV1Dto.ProductResponse {
        val brand = brandJpaRepository.save(Brand(name = brandName))
        val product = productJpaRepository.save(
            Product(
                brandId = brand.id,
                name = productName,
                price = price,
            ),
        )
        productStatsJpaRepository.save(ProductStats(productId = product.id))
        productStockJpaRepository.save(ProductStock(productId = product.id, stockQuantity = stockQuantity))
        return CatalogV1Dto.ProductResponse(product.id, brand.id, product.name, product.price, product.status)
    }

    private fun saveUser(loginId: String) {
        userJpaRepository.save(
            User(
                loginId = loginId,
                encryptedPassword = passwordEncoder.encode(RawPassword("abcd1234")),
                name = "홍길동",
                birthdate = LocalDate.of(1990, 1, 1),
                email = "$loginId@example.com",
                role = UserRole.CONSUMER,
            ),
        )
    }

    private fun authHeaders(loginId: String = "loopers01"): HttpHeaders = HttpHeaders().apply {
        add("X-Loopers-LoginId", loginId)
        add("X-Loopers-LoginPw", "abcd1234")
    }
}
