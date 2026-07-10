package com.loopers.interfaces.api.order

import com.loopers.application.user.UserFacade
import com.loopers.domain.brand.BrandService
import com.loopers.domain.coupon.CouponService
import com.loopers.domain.coupon.DiscountType
import com.loopers.domain.coupon.UserCouponService
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.ProductStatus
import com.loopers.domain.queue.EntryTokenService
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.interfaces.api.ApiResponse
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
import java.time.ZonedDateTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderCouponV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val userFacade: UserFacade,
    private val brandService: BrandService,
    private val productService: ProductService,
    private val couponService: CouponService,
    private val userCouponService: UserCouponService,
    private val productJpaRepository: ProductJpaRepository,
    private val entryTokenService: EntryTokenService,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private val loginId = "user123"
    private val rawPassword = "Valid1!pw"

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun signUp(): Long =
        userFacade.signUp(loginId, rawPassword, "홍길동", LocalDate.of(1994, 7, 14), "hong@example.com").id

    private fun authHeaders() = HttpHeaders().apply {
        set("X-Loopers-LoginId", loginId)
        set("X-Loopers-LoginPw", rawPassword)
    }

    /** 유효한 입장 토큰을 발급해 실은 주문용 헤더. (게이트 통과용) */
    private fun orderHeaders(userId: Long) = authHeaders().apply {
        set("X-Entry-Token", entryTokenService.issue(userId))
    }

    private fun rateCoupon() = couponService.register(
        name = "10% 할인",
        discountType = DiscountType.RATE,
        discountValue = 10,
        minOrderAmount = 10_000,
        expiredAt = ZonedDateTime.parse("2026-12-31T23:59:59+09:00"),
    )

    private val responseType = object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {}

    @DisplayName("쿠폰을 적용해 주문하면, 할인이 반영되고 쿠폰이 사용 처리된다.")
    @Test
    fun appliesCouponAndMarksUsed() {
        // arrange
        val userId = signUp()
        val brand = brandService.register("Nike")
        val product = productService.register(brand.id, "Air Max", 1_000, 100, ProductStatus.ON_SALE)
        val coupon = rateCoupon()
        userCouponService.issue(userId, coupon.id)
        // 상품 총액 15_000 (1_000 x 15)
        val request = OrderV1Dto.PlaceOrderRequest(
            items = listOf(OrderV1Dto.OrderLineRequest(productId = product.id, quantity = 15)),
            couponId = coupon.id,
        )

        // act
        val response = testRestTemplate.exchange(
            "/api/v1/orders",
            HttpMethod.POST,
            HttpEntity(request, orderHeaders(userId)),
            responseType,
        )

        // assert
        assertAll(
            { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
            { assertThat(response.body?.data?.totalPrice).isEqualTo(15_000L) },
            { assertThat(response.body?.data?.discountAmount).isEqualTo(1_500L) },
            { assertThat(response.body?.data?.finalAmount).isEqualTo(13_500L) },
            { assertThat(response.body?.data?.couponId).isEqualTo(coupon.id) },
        )
        // 쿠폰이 USED 로 전이되어 재사용 시 충돌
        val myCoupon = userCouponService.getMyCoupons(userId, org.springframework.data.domain.PageRequest.of(0, 10)).content.first()
        assertThat(myCoupon.status).isEqualTo(com.loopers.domain.coupon.CouponStatus.USED)
    }

    @DisplayName("쿠폰 없이 주문하면, 할인 금액은 0이고 최종 금액은 총액과 같다.")
    @Test
    fun placesWithoutCoupon() {
        // arrange
        val userId = signUp()
        val brand = brandService.register("Nike")
        val product = productService.register(brand.id, "Air Max", 1_000, 100, ProductStatus.ON_SALE)
        val request = OrderV1Dto.PlaceOrderRequest(
            items = listOf(OrderV1Dto.OrderLineRequest(productId = product.id, quantity = 3)),
            couponId = null,
        )

        // act
        val response = testRestTemplate.exchange(
            "/api/v1/orders",
            HttpMethod.POST,
            HttpEntity(request, orderHeaders(userId)),
            responseType,
        )

        // assert
        assertAll(
            { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
            { assertThat(response.body?.data?.discountAmount).isEqualTo(0L) },
            { assertThat(response.body?.data?.finalAmount).isEqualTo(3_000L) },
            { assertThat(response.body?.data?.couponId).isNull() },
        )
    }

    @DisplayName("이미 사용한 쿠폰으로 주문하면, 409 CONFLICT 가 발생하고 재고가 차감되지 않는다 (롤백).")
    @Test
    fun rollsBackStock_whenCouponAlreadyUsed() {
        // arrange
        val userId = signUp()
        val brand = brandService.register("Nike")
        val product = productService.register(brand.id, "Air Max", 1_000, 100, ProductStatus.ON_SALE)
        val coupon = rateCoupon()
        userCouponService.issue(userId, coupon.id)
        userCouponService.use(userId, coupon.id, orderAmount = 50_000, now = ZonedDateTime.parse("2026-06-12T00:00:00+09:00"))
        val request = OrderV1Dto.PlaceOrderRequest(
            items = listOf(OrderV1Dto.OrderLineRequest(productId = product.id, quantity = 15)),
            couponId = coupon.id,
        )

        // act
        val response = testRestTemplate.exchange(
            "/api/v1/orders",
            HttpMethod.POST,
            HttpEntity(request, orderHeaders(userId)),
            responseType,
        )

        // assert
        // 재고가 차감되지 않고 그대로 유지되어야 한다 (롤백)
        val reloaded = productJpaRepository.findById(product.id).orElseThrow()
        assertAll(
            { assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT) },
            { assertThat(reloaded.stock.quantity).isEqualTo(100) },
        )
    }

    @DisplayName("타 유저 소유 쿠폰으로 주문하면, 404 NOT_FOUND 가 발생한다.")
    @Test
    fun returnsNotFound_whenCouponNotOwned() {
        // arrange
        val userId = signUp()
        val brand = brandService.register("Nike")
        val product = productService.register(brand.id, "Air Max", 1_000, 100, ProductStatus.ON_SALE)
        val coupon = rateCoupon()
        // 다른 유저(999L)가 소유한 쿠폰
        userCouponService.issue(userId = 999L, couponId = coupon.id)
        val request = OrderV1Dto.PlaceOrderRequest(
            items = listOf(OrderV1Dto.OrderLineRequest(productId = product.id, quantity = 15)),
            couponId = coupon.id,
        )

        // act
        val response = testRestTemplate.exchange(
            "/api/v1/orders",
            HttpMethod.POST,
            HttpEntity(request, orderHeaders(userId)),
            responseType,
        )

        // assert
        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }
}
