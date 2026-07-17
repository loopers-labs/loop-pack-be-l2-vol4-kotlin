package com.loopers.interfaces.api.order

import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductStockModel
import com.loopers.domain.product.ProductStockRepository
import com.loopers.domain.queue.OrderQueueRepository
import com.loopers.domain.user.UserService
import com.loopers.interfaces.api.ApiResponse
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
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
import java.math.BigDecimal
import java.time.LocalDate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderQueueGateE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val userService: UserService,
    private val productRepository: ProductRepository,
    private val productStockRepository: ProductStockRepository,
    private val orderQueueRepository: OrderQueueRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    private var userId = 0L
    private var productId = 0L

    @BeforeEach
    fun setUp() {
        val user = userService.signUp(
            UserService.SignUpCommand(
                loginId = LOGIN_ID,
                password = PASSWORD,
                name = "테스터",
                birthDate = LocalDate.of(1990, 1, 1),
                email = "$LOGIN_ID@loopers.com",
            ),
        )
        userId = user.id
        val product = productRepository.save(
            ProductModel(brandId = 1L, name = "상품", description = "설명", price = BigDecimal("10000")),
        )
        productId = product.id
        productStockRepository.save(ProductStockModel(productId = productId, quantity = 10))
    }

    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("POST /api/v1/orders — X-Entry-Token 헤더가 없으면 429 TOO_MANY_REQUESTS를 반환한다.")
    @Test
    fun returnsTooManyRequests_whenEntryTokenHeaderIsMissing() {
        // act
        val response = order(entryToken = null)

        // assert
        assertAll(
            { assertThat(response.statusCode).isEqualTo(HttpStatus.TOO_MANY_REQUESTS) },
            { assertThat(response.body?.meta?.errorCode).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.reasonPhrase) },
        )
    }

    @DisplayName("POST /api/v1/orders — 유효한 X-Entry-Token이면 주문에 성공하고 토큰은 소비된다.")
    @Test
    fun succeedsAndConsumesToken_whenEntryTokenIsValid() {
        // arrange
        orderQueueRepository.issueToken(userId, "tok-e2e", 300, System.currentTimeMillis())

        // act
        val response = order(entryToken = "tok-e2e")

        // assert
        assertAll(
            { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(response.body?.data?.id).isNotNull() },
            { assertThat(orderQueueRepository.findToken(userId)).isNull() },
        )
    }

    @DisplayName("POST /api/v1/orders — 소비된 토큰을 재사용하면 429 TOO_MANY_REQUESTS를 반환한다.")
    @Test
    fun blocksReuse_whenTokenIsAlreadyConsumed() {
        // arrange
        orderQueueRepository.issueToken(userId, "tok-e2e", 300, System.currentTimeMillis())
        val first = order(entryToken = "tok-e2e")
        assertThat(first.statusCode).isEqualTo(HttpStatus.OK)

        // act: 같은 토큰으로 재주문
        val second = order(entryToken = "tok-e2e")

        // assert
        assertThat(second.statusCode).isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
    }

    @DisplayName("POST /api/v1/orders — 잘못된 X-Entry-Token이면 429 TOO_MANY_REQUESTS를 반환한다.")
    @Test
    fun returnsTooManyRequests_whenEntryTokenIsWrong() {
        // arrange
        orderQueueRepository.issueToken(userId, "tok-e2e", 300, System.currentTimeMillis())

        // act
        val response = order(entryToken = "wrong-token")

        // assert
        assertThat(response.statusCode).isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
    }

    private fun order(entryToken: String?) = testRestTemplate.exchange(
        "/api/v1/orders",
        HttpMethod.POST,
        HttpEntity(orderRequest(), headers(entryToken)),
        object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {},
    )

    private fun orderRequest() = OrderV1Dto.OrderRequest(
        items = listOf(OrderV1Dto.OrderItemRequest(productId = productId, quantity = 1)),
    )

    private fun headers(entryToken: String?) = HttpHeaders().apply {
        set("X-Loopers-LoginId", LOGIN_ID)
        set("X-Loopers-LoginPw", PASSWORD)
        entryToken?.let { set("X-Entry-Token", it) }
    }

    companion object {
        private const val LOGIN_ID = "tester"
        private const val PASSWORD = "Password1!"
    }
}
