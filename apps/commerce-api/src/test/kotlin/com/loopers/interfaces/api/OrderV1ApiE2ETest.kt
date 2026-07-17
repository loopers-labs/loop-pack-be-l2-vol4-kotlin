package com.loopers.interfaces.api

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
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import java.time.LocalDate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val entryTokenRepository: EntryTokenRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val stockJpaRepository: StockJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    companion object {
        private const val ORDER_ENDPOINT = "/api/v1/orders"
        private const val USER_ENDPOINT = "/api/v1/users"
        private const val LOGIN_ID = "seondays"
        private const val PASSWORD = "Password1!"
        private const val VALID_TOKEN = "valid-token"
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @DisplayName("POST /api/v1/orders - 대기열 입장 토큰 검증")
    @Nested
    inner class QueueTokenGate {
        @DisplayName("입장 토큰 없이 주문하면 403을 반환한다.")
        @Test
        fun returnsForbidden_whenNoQueueToken() {
            // arrange
            signUp()

            // act
            val response = placeOrder(authHeaders(queueToken = null))

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        }

        @DisplayName("발급된 토큰과 다른 토큰으로 주문하면 403을 반환한다.")
        @Test
        fun returnsForbidden_whenTokenMismatch() {
            // arrange
            val userId = signUp()
            entryTokenRepository.save(userId, EntryToken(VALID_TOKEN))

            // act
            val response = placeOrder(authHeaders(queueToken = "wrong-token"))

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        }

        @DisplayName("유효한 토큰으로 주문하면 201과 함께 주문이 생성된다.")
        @Test
        fun createsOrder_whenValidToken() {
            // arrange
            val context = admittedUserWithProduct()

            // act
            val response = placeOrder(context.headers, context.productId)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
        }

        @DisplayName("주문에 성공하면 입장 토큰이 삭제된다.")
        @Test
        fun deletesToken_afterSuccessfulOrder() {
            // arrange
            val context = admittedUserWithProduct()

            // act
            val response = placeOrder(context.headers, context.productId)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED) },
                { assertThat(entryTokenRepository.find(context.userId)).isNull() },
            )
        }

        @DisplayName("주문에 성공한 뒤 같은 토큰으로 다시 주문하면 403을 반환한다.")
        @Test
        fun returnsForbidden_whenReusingConsumedToken() {
            // arrange
            val context = admittedUserWithProduct()
            val firstResponse = placeOrder(context.headers, context.productId)

            // act
            val secondResponse = placeOrder(context.headers, context.productId)

            // assert
            assertAll(
                { assertThat(firstResponse.statusCode).isEqualTo(HttpStatus.CREATED) },
                { assertThat(secondResponse.statusCode).isEqualTo(HttpStatus.FORBIDDEN) },
            )
        }
    }

    private data class OrderContext(
        val userId: Long,
        val headers: HttpHeaders,
        val productId: Long,
    )

    private fun admittedUserWithProduct(token: String = VALID_TOKEN): OrderContext {
        val userId = signUp()
        entryTokenRepository.save(userId, EntryToken(token))
        val product = saveProductWithStock(stock = 10)
        return OrderContext(
            userId = userId,
            headers = authHeaders(queueToken = token),
            productId = product.id,
        )
    }

    private fun placeOrder(headers: HttpHeaders, productId: Long = 1L) =
        testRestTemplate.exchange(
            ORDER_ENDPOINT,
            HttpMethod.POST,
            HttpEntity(orderRequest(productId), headers),
            object : ParameterizedTypeReference<ApiResponse<Any>>() {},
        )

    private fun signUp(): Long {
        val signUpRequest = UserV1Dto.SignUpRequest(
            loginId = LOGIN_ID,
            password = PASSWORD,
            name = "선데이",
            birthDate = LocalDate.of(1990, 1, 1),
            email = "seondays@example.com",
        )
        val jsonHeaders = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val response = testRestTemplate.exchange(
            USER_ENDPOINT,
            HttpMethod.POST,
            HttpEntity(signUpRequest, jsonHeaders),
            object : ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>>() {},
        )
        return response.body?.data?.id ?: error("회원 가입 응답에 id가 없습니다.")
    }

    private fun authHeaders(queueToken: String?): HttpHeaders =
        HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-Loopers-LoginId", LOGIN_ID)
            set("X-Loopers-LoginPw", PASSWORD)
            queueToken?.let { set("X-Loopers-QueueToken", it) }
        }

    private fun orderRequest(productId: Long = 1L): Map<String, Any> =
        mapOf("items" to listOf(mapOf("productId" to productId, "quantity" to 1)))

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
}
