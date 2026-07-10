package com.loopers.interfaces.api.order

import com.loopers.application.user.UserFacade
import com.loopers.domain.brand.BrandService
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.ProductStatus
import com.loopers.domain.queue.EntryTokenService
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
import java.time.LocalDate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val userFacade: UserFacade,
    private val brandService: BrandService,
    private val productService: ProductService,
    private val productJpaRepository: ProductJpaRepository,
    private val entryTokenService: EntryTokenService,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private val loginId = "user123"
    private val rawPassword = "Valid1!pw"
    private var userId: Long = 0L

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun signUp() {
        userId = userFacade.signUp(loginId, rawPassword, "홍길동", LocalDate.of(1994, 7, 14), "hong@example.com").id
    }

    private fun authHeaders(id: String = loginId, pw: String = rawPassword) = HttpHeaders().apply {
        set("X-Loopers-LoginId", id)
        set("X-Loopers-LoginPw", pw)
    }

    /** 유효한 입장 토큰을 발급해 실은 주문용 헤더. (게이트 통과용) */
    private fun orderHeaders() = authHeaders().apply {
        set("X-Entry-Token", entryTokenService.issue(userId))
    }

    @DisplayName("POST /api/v1/orders")
    @Nested
    inner class Place {
        @DisplayName("유효한 라인으로 요청하면, PENDING 주문이 생성되고 재고가 차감된다.")
        @Test
        fun placesPendingAndDeductsStock() {
            // arrange
            signUp()
            val brand = brandService.register("Nike")
            val product = productService.register(brand.id, "Air Max", 1_000, 10, ProductStatus.ON_SALE)
            val request = OrderV1Dto.PlaceOrderRequest(
                items = listOf(OrderV1Dto.OrderLineRequest(productId = product.id, quantity = 3)),
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {}
            val response = testRestTemplate.exchange(
                "/api/v1/orders",
                HttpMethod.POST,
                HttpEntity(request, orderHeaders()),
                responseType,
            )

            // assert
            val reloaded = productJpaRepository.findById(product.id).orElseThrow()
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.status).isEqualTo(OrderStatus.PENDING) },
                { assertThat(response.body?.data?.totalPrice).isEqualTo(3 * 1_000L) },
                { assertThat(response.body?.data?.items?.size).isEqualTo(1) },
                { assertThat(reloaded.stock.quantity).isEqualTo(7) },
            )
        }

        @DisplayName("재고가 부족하면, 409 CONFLICT 응답을 받고 재고는 변하지 않는다 (all-or-nothing).")
        @Test
        fun returnsConflictAndDoesNotDeduct_whenInsufficient() {
            // arrange
            signUp()
            val brand = brandService.register("Nike")
            val product = productService.register(brand.id, "Air Max", 1_000, 2, ProductStatus.ON_SALE)
            val request = OrderV1Dto.PlaceOrderRequest(
                items = listOf(OrderV1Dto.OrderLineRequest(product.id, quantity = 5)),
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {}
            val response = testRestTemplate.exchange(
                "/api/v1/orders",
                HttpMethod.POST,
                HttpEntity(request, orderHeaders()),
                responseType,
            )

            // assert
            val reloaded = productJpaRepository.findById(product.id).orElseThrow()
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT) },
                { assertThat(reloaded.stock.quantity).isEqualTo(2) },
            )
        }

        @DisplayName("존재하지 않거나 HIDDEN 상품이 포함되면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenProductIsHiddenOrAbsent() {
            // arrange
            signUp()
            val brand = brandService.register("Nike")
            val hidden = productService.register(brand.id, "Hidden", 1_000, 10, ProductStatus.HIDDEN)
            val request = OrderV1Dto.PlaceOrderRequest(
                items = listOf(OrderV1Dto.OrderLineRequest(hidden.id, quantity = 1)),
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {}
            val response = testRestTemplate.exchange(
                "/api/v1/orders",
                HttpMethod.POST,
                HttpEntity(request, orderHeaders()),
                responseType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("인증 실패 시, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        fun returnsUnauthorized() {
            // arrange
            signUp()
            val brand = brandService.register("Nike")
            val product = productService.register(brand.id, "P", 1_000, 10, ProductStatus.ON_SALE)
            val request = OrderV1Dto.PlaceOrderRequest(items = listOf(OrderV1Dto.OrderLineRequest(product.id, 1)))

            // act : 인증이 토큰 검증보다 먼저이므로, 토큰 헤더는 있으나 비밀번호가 틀리면 401
            val headers = authHeaders(pw = "WrongPw1!").apply { set("X-Entry-Token", "any-token") }
            val responseType = object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {}
            val response = testRestTemplate.exchange(
                "/api/v1/orders",
                HttpMethod.POST,
                HttpEntity(request, headers),
                responseType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }

    @DisplayName("GET /api/v1/orders/{orderId}")
    @Nested
    inner class GetOrder {
        @DisplayName("본인 주문 ID로 요청하면, 상세를 반환한다.")
        @Test
        fun returnsMyOrder() {
            // arrange
            signUp()
            val brand = brandService.register("Nike")
            val product = productService.register(brand.id, "P", 1_000, 10, ProductStatus.ON_SALE)
            val placeReq = OrderV1Dto.PlaceOrderRequest(items = listOf(OrderV1Dto.OrderLineRequest(product.id, 2)))
            val placeType = object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {}
            val placed = testRestTemplate.exchange(
                "/api/v1/orders",
                HttpMethod.POST,
                HttpEntity(placeReq, orderHeaders()),
                placeType,
            )
            val orderId = placed.body?.data?.id ?: error("주문 생성 실패")

            // act
            val response = testRestTemplate.exchange(
                "/api/v1/orders/$orderId",
                HttpMethod.GET,
                HttpEntity<Any>(authHeaders()),
                placeType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.id).isEqualTo(orderId) },
            )
        }

        @DisplayName("존재하지 않거나 본인 것이 아니면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenAbsentOrNotOwned() {
            // arrange
            signUp()

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {}
            val response = testRestTemplate.exchange(
                "/api/v1/orders/999",
                HttpMethod.GET,
                HttpEntity<Any>(authHeaders()),
                responseType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }

    @DisplayName("GET /api/v1/orders?startAt=&endAt=")
    @Nested
    inner class GetOrders {
        @DisplayName("기간 내 본인 주문 목록을 반환한다.")
        @Test
        fun returnsMyOrdersWithinPeriod() {
            // arrange
            signUp()
            val brand = brandService.register("Nike")
            val product = productService.register(brand.id, "P", 1_000, 10, ProductStatus.ON_SALE)
            val placeReq = OrderV1Dto.PlaceOrderRequest(items = listOf(OrderV1Dto.OrderLineRequest(product.id, 1)))
            val placeType = object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {}
            testRestTemplate.exchange(
                "/api/v1/orders",
                HttpMethod.POST,
                HttpEntity(placeReq, orderHeaders()),
                placeType,
            )

            val today = LocalDate.now()
            val url = "/api/v1/orders?startAt=${today.minusDays(1)}&endAt=${today.plusDays(1)}"

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderPageResponse>>() {}
            val response = testRestTemplate.exchange(url, HttpMethod.GET, HttpEntity<Any>(authHeaders()), responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.totalElements).isEqualTo(1) },
            )
        }
    }
}
