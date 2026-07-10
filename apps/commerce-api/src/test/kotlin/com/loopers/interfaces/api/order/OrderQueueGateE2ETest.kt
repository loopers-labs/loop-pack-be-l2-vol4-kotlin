package com.loopers.interfaces.api.order

import com.loopers.application.user.UserFacade
import com.loopers.domain.brand.BrandService
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.ProductStatus
import com.loopers.domain.queue.EntryTokenService
import com.loopers.interfaces.api.ApiResponse
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
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

/**
 * 주문 API 앞단의 입장 토큰 게이트(Step 2c) 검증.
 * (입장 스케줄러는 test 프로파일에서 비활성이므로) 토큰을 직접 발급/검증하는 흐름만 결정적으로 확인한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderQueueGateE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val userFacade: UserFacade,
    private val brandService: BrandService,
    private val productService: ProductService,
    private val entryTokenService: EntryTokenService,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    private val loginId = "user123"
    private val rawPassword = "Valid1!pw"
    private var userId: Long = 0L
    private var productId: Long = 0L
    private val responseType = object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {}

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    private fun arrange() {
        userId = userFacade.signUp(loginId, rawPassword, "홍길동", LocalDate.of(1994, 7, 14), "hong@example.com").id
        val brand = brandService.register("Nike")
        productId = productService.register(brand.id, "Air Max", 1_000, 10, ProductStatus.ON_SALE).id
    }

    private fun headers(token: String) = HttpHeaders().apply {
        set("X-Loopers-LoginId", loginId)
        set("X-Loopers-LoginPw", rawPassword)
        set("X-Entry-Token", token)
    }

    private fun order(token: String) = testRestTemplate.exchange(
        "/api/v1/orders",
        HttpMethod.POST,
        HttpEntity(
            OrderV1Dto.PlaceOrderRequest(items = listOf(OrderV1Dto.OrderLineRequest(productId = productId, quantity = 1))),
            headers(token),
        ),
        responseType,
    )

    @DisplayName("유효한 입장 토큰으로 주문하면 성공하고, 같은 토큰을 재사용하면 401 이다. (1회용)")
    @Test
    fun validTokenSucceeds_thenReuseFails() {
        // arrange
        arrange()
        val token = entryTokenService.issue(userId)

        // act
        val first = order(token)
        val second = order(token)

        // assert
        assertAll(
            { assertThat(first.statusCode.is2xxSuccessful).isTrue() },
            { assertThat(second.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED) },
        )
    }

    @DisplayName("발급되지 않은(틀린) 토큰으로 주문하면 401 이다.")
    @Test
    fun wrongTokenFails() {
        // arrange
        arrange()

        // act
        val response = order("not-a-real-token")

        // assert
        assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
    }
}
