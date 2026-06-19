package com.loopers.interfaces.api.shopping

import com.loopers.application.catalog.CatalogApplicationService
import com.loopers.domain.catalog.CatalogCommand
import com.loopers.domain.user.PasswordEncoder
import com.loopers.domain.user.RawPassword
import com.loopers.domain.user.User
import com.loopers.domain.user.UserRole
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.order.OrderV1Dto
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
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
class CartV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val catalogApplicationService: CatalogApplicationService,
    private val userJpaRepository: UserJpaRepository,
    private val passwordEncoder: PasswordEncoder,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("POST /api/v1/cart/items 는 상품을 쇼핑카트에 담고 GET /api/v1/cart 는 현재 Catalog 정보와 조합해 반환한다.")
    @Test
    fun addsItemAndGetsCart() {
        saveConsumer()
        val productId = createProduct()
        val unitType = object : ParameterizedTypeReference<ApiResponse<Unit>>() {}
        val cartType = object : ParameterizedTypeReference<ApiResponse<CartV1Dto.CartResponse>>() {}

        val addResponse = testRestTemplate.exchange(
            "/api/v1/cart/items",
            HttpMethod.POST,
            HttpEntity(mapOf("productId" to productId, "quantity" to 2), authHeaders()),
            unitType,
        )
        val getResponse = testRestTemplate.exchange(
            "/api/v1/cart",
            HttpMethod.GET,
            HttpEntity<Any>(authHeaders()),
            cartType,
        )

        assertAll(
            { assertThat(addResponse.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(getResponse.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(getResponse.body?.data?.items).hasSize(1) },
            { assertThat(getResponse.body?.data?.items?.get(0)?.productId).isEqualTo(productId) },
            { assertThat(getResponse.body?.data?.items?.get(0)?.productName).isEqualTo("Air Max") },
            { assertThat(getResponse.body?.data?.items?.get(0)?.brandName).isEqualTo("Nike") },
            { assertThat(getResponse.body?.data?.items?.get(0)?.price).isEqualTo(129000L) },
            { assertThat(getResponse.body?.data?.items?.get(0)?.quantity).isEqualTo(2) },
            { assertThat(getResponse.body?.data?.items?.get(0)?.orderable).isTrue() },
        )
    }

    @DisplayName("GET /api/v1/cart/count 는 쇼핑카트에 담긴 상품 라인 수를 반환한다.")
    @Test
    fun countsCartProductLines() {
        saveConsumer()
        val firstProductId = createProduct(name = "Air Max", brandName = "Nike")
        val secondProductId = createProduct(name = "Gazelle", brandName = "Adidas")
        val unitType = object : ParameterizedTypeReference<ApiResponse<Unit>>() {}
        val countType = object : ParameterizedTypeReference<ApiResponse<Map<String, Int>>>() {}
        testRestTemplate.exchange(
            "/api/v1/cart/items",
            HttpMethod.POST,
            HttpEntity(mapOf("productId" to firstProductId, "quantity" to 2), authHeaders()),
            unitType,
        )
        testRestTemplate.exchange(
            "/api/v1/cart/items",
            HttpMethod.POST,
            HttpEntity(mapOf("productId" to secondProductId, "quantity" to 3), authHeaders()),
            unitType,
        )

        val countResponse = testRestTemplate.exchange(
            "/api/v1/cart/count",
            HttpMethod.GET,
            HttpEntity<Any>(authHeaders()),
            countType,
        )

        assertAll(
            { assertThat(countResponse.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(countResponse.body?.data?.get("count")).isEqualTo(2) },
        )
    }

    @DisplayName("POST /api/v1/cart/checkout 은 기존 Order checkout 으로 넘기고 성공 후 쇼핑카트를 비운다.")
    @Test
    fun checksOutFromCartAndClearsCartAfterSuccess() {
        saveConsumer()
        val productId = createProduct()
        val unitType = object : ParameterizedTypeReference<ApiResponse<Unit>>() {}
        val orderType = object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {}
        val cartType = object : ParameterizedTypeReference<ApiResponse<CartV1Dto.CartResponse>>() {}
        testRestTemplate.exchange(
            "/api/v1/cart/items",
            HttpMethod.POST,
            HttpEntity(mapOf("productId" to productId, "quantity" to 2), authHeaders()),
            unitType,
        )

        val checkoutResponse = testRestTemplate.exchange(
            "/api/v1/cart/checkout",
            HttpMethod.POST,
            HttpEntity(
                mapOf(
                    "deliveryAddress" to "서울시 강남구",
                    "deliveryRequest" to "문 앞",
                    "phoneNumber" to "010-1234-5678",
                    "reservationExpiresAt" to LocalDateTime.now().plusMinutes(10).toString(),
                ),
                authHeaders(),
            ),
            orderType,
        )
        val cartResponse = testRestTemplate.exchange(
            "/api/v1/cart",
            HttpMethod.GET,
            HttpEntity<Any>(authHeaders()),
            cartType,
        )

        assertAll(
            { assertThat(checkoutResponse.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(checkoutResponse.body?.data?.items).hasSize(1) },
            { assertThat(checkoutResponse.body?.data?.items?.get(0)?.productId).isEqualTo(productId) },
            { assertThat(checkoutResponse.body?.data?.items?.get(0)?.productNameSnapshot).isEqualTo("Air Max") },
            { assertThat(cartResponse.body?.data?.items).isEmpty() },
        )
    }

    private fun createProduct(
        name: String = "Air Max",
        brandName: String = "Nike",
    ): Long {
        val brand = catalogApplicationService.createBrand(CatalogCommand.CreateBrand(brandName))
        return catalogApplicationService.createProduct(
            CatalogCommand.CreateProduct(
                brandId = brand.brandId,
                name = name,
                price = 129000L,
                initialStock = 5,
                detailImageUrls = emptyList(),
            ),
        ).productId
    }

    private fun saveConsumer() {
        userJpaRepository.save(
            User(
                loginId = "loopers01",
                encryptedPassword = passwordEncoder.encode(RawPassword("abcd1234")),
                name = "Consumer",
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
