package com.loopers.interfaces.api.order

import com.loopers.domain.order.OrderStatus
import com.loopers.domain.user.PasswordEncoder
import com.loopers.infrastructure.brand.BrandEntity
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.inventory.InventoryEntity
import com.loopers.infrastructure.inventory.InventoryJpaRepository
import com.loopers.infrastructure.member.MemberEntity
import com.loopers.infrastructure.member.MemberJpaRepository
import com.loopers.infrastructure.order.OrderJpaRepository
import com.loopers.infrastructure.product.ProductEntity
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.interfaces.api.ApiResponse
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
import org.springframework.jdbc.core.JdbcTemplate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val memberJpaRepository: MemberJpaRepository,
    private val brandJpaRepository: BrandJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val inventoryJpaRepository: InventoryJpaRepository,
    private val orderJpaRepository: OrderJpaRepository,
    private val jdbcTemplate: JdbcTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("POST /api/v1/orders")
    @Nested
    inner class PlaceOrder {
        @DisplayName("여러 상품을 주문하면 주문을 저장하고 재고를 차감한다")
        @Test
        fun placesOrder() {
            val member = createMember()
            val brand = createBrand()
            val firstProduct = createProduct(brandId = brand.id, name = "hoodie", price = 10_000L)
            val secondProduct = createProduct(brandId = brand.id, name = "cap", price = 5_000L)
            createInventory(productId = firstProduct.id, quantity = 10L)
            createInventory(productId = secondProduct.id, quantity = 3L)

            val response = testRestTemplate.exchange(
                ORDERS_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(
                    OrderV1Dto.CreateOrderRequest(
                        items = listOf(
                            OrderV1Dto.CreateOrderRequest.Item(productId = firstProduct.id, quantity = 2L),
                            OrderV1Dto.CreateOrderRequest.Item(productId = secondProduct.id, quantity = 1L),
                        ),
                    ),
                    createAuthHeaders(),
                ),
                object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {},
            )

            val orders = orderJpaRepository.findAll()
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.memberId).isEqualTo(member.id) },
                { assertThat(response.body?.data?.status).isEqualTo(OrderStatus.COMPLETED) },
                { assertThat(response.body?.data?.totalAmount).isEqualTo(25_000L) },
                { assertThat(response.body?.data?.items).hasSize(2) },
                { assertThat(orders).hasSize(1) },
                { assertThat(countOrderItems()).isEqualTo(2) },
                { assertThat(inventoryJpaRepository.findByProductId(firstProduct.id)?.quantity).isEqualTo(8L) },
                { assertThat(inventoryJpaRepository.findByProductId(secondProduct.id)?.quantity).isEqualTo(2L) },
            )
        }

        @DisplayName("재고가 부족하면 주문을 저장하지 않고 재고를 차감하지 않는다")
        @Test
        fun returnsConflict_whenInventoryIsInsufficient() {
            createMember()
            val brand = createBrand()
            val product = createProduct(brandId = brand.id)
            createInventory(productId = product.id, quantity = 1L)

            val response = testRestTemplate.exchange(
                ORDERS_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(
                    OrderV1Dto.CreateOrderRequest(
                        items = listOf(OrderV1Dto.CreateOrderRequest.Item(productId = product.id, quantity = 2L)),
                    ),
                    createAuthHeaders(),
                ),
                object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT) },
                { assertThat(orderJpaRepository.findAll()).isEmpty() },
                { assertThat(inventoryJpaRepository.findByProductId(product.id)?.quantity).isEqualTo(1L) },
            )
        }

        @DisplayName("존재하지 않는 상품은 주문할 수 없다")
        @Test
        fun returnsNotFound_whenProductDoesNotExist() {
            createMember()

            val response = testRestTemplate.exchange(
                ORDERS_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(
                    OrderV1Dto.CreateOrderRequest(
                        items = listOf(OrderV1Dto.CreateOrderRequest.Item(productId = 999L, quantity = 1L)),
                    ),
                    createAuthHeaders(),
                ),
                object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("인증 정보가 올바르지 않으면 주문할 수 없다")
        @Test
        fun returnsUnauthorized_whenCredentialsAreInvalid() {
            createMember()
            val brand = createBrand()
            val product = createProduct(brandId = brand.id)
            createInventory(productId = product.id, quantity = 1L)

            val response = testRestTemplate.exchange(
                ORDERS_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(
                    OrderV1Dto.CreateOrderRequest(
                        items = listOf(OrderV1Dto.CreateOrderRequest.Item(productId = product.id, quantity = 1L)),
                    ),
                    createAuthHeaders(password = "Wrong123!"),
                ),
                object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }

    private fun createMember(
        loginId: String = LOGIN_ID,
        password: String = RAW_PASSWORD,
    ): MemberEntity {
        return memberJpaRepository.save(
            MemberEntity(
                loginId = loginId,
                password = PasswordEncoder.encode(password),
                name = "홍길동",
                birthDate = java.time.LocalDate.of(1990, 1, 1),
                email = "$loginId@example.com",
            ),
        )
    }

    private fun createBrand(): BrandEntity {
        return brandJpaRepository.save(
            BrandEntity(
                name = "loopers",
                description = "loopers brand",
                logoImageUrl = "https://image.loopers/brand.png",
            ),
        )
    }

    private fun createProduct(
        brandId: Long,
        name: String = "loopers hoodie",
        price: Long = 10_000L,
    ): ProductEntity {
        return productJpaRepository.save(
            ProductEntity(
                brandId = brandId,
                name = name,
                price = price,
                description = "loopers product",
                imageUrl = "https://image.loopers/product.png",
            ),
        )
    }

    private fun createInventory(productId: Long, quantity: Long): InventoryEntity {
        return inventoryJpaRepository.save(
            InventoryEntity(
                productId = productId,
                quantity = quantity,
            ),
        )
    }

    private fun createAuthHeaders(
        loginId: String = LOGIN_ID,
        password: String = RAW_PASSWORD,
    ): HttpHeaders {
        return HttpHeaders().apply {
            set("X-Loopers-LoginId", loginId)
            set("X-Loopers-LoginPw", password)
        }
    }

    private fun countOrderItems(): Long {
        return jdbcTemplate.queryForObject("select count(*) from order_item", Long::class.java) ?: 0L
    }

    private companion object {
        private const val ORDERS_ENDPOINT = "/api/v1/orders"
        private const val LOGIN_ID = "loopers123"
        private const val RAW_PASSWORD = "Loopers123!"
    }
}
