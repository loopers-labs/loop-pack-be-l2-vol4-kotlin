package com.loopers.interfaces.api

import com.loopers.ApiTest
import com.loopers.domain.brand.application.service.BrandService
import com.loopers.domain.brand.support.BrandSteps.Companion.브랜드_등록_커맨드
import com.loopers.domain.coupon.application.command.CouponTemplateCommand
import com.loopers.domain.coupon.application.service.CouponIssueRequestWorker
import com.loopers.domain.coupon.application.service.CouponService
import com.loopers.domain.product.application.ProductFacade
import com.loopers.domain.product.infrastructure.persistence.stock.ProductStockJpaRepository
import com.loopers.domain.product.support.ProductSteps.Companion.상품_등록_커맨드
import com.loopers.domain.user.application.service.UserService
import com.loopers.domain.user.support.UserSteps.Companion.기본_로그인_ID
import com.loopers.domain.user.support.UserSteps.Companion.기본_비밀번호
import com.loopers.domain.user.support.UserSteps.Companion.사용자_회원가입
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.time.LocalDateTime
import java.util.UUID

class OrderCouponFailureApiE2ETest
    @Autowired
    constructor(
        private val userService: UserService,
        private val brandService: BrandService,
        private val couponService: CouponService,
        private val couponIssueRequestWorker: CouponIssueRequestWorker,
        private val productFacade: ProductFacade,
        private val productStockJpaRepository: ProductStockJpaRepository,
    ) : ApiTest() {
        private val mapResponseType =
            object : ParameterizedTypeReference<ApiResponse<Map<String, Any?>>>() {}
        private val listResponseType =
            object : ParameterizedTypeReference<ApiResponse<List<Map<String, Any?>>>>() {}

        @Test
        fun `만료된_쿠폰으로_주문하면_409_CONFLICT를_반환하고_재고를_차감하지_않는다`() {
            userService.signUp(사용자_회원가입())
            val productId = registerProduct(price = 10_000, initialStock = 5)
            val templateId = createRateTemplate(value = 10, minOrderAmount = 0)
            val issuedCouponId = issueCoupon(templateId)
            expireTemplate(templateId)

            val response = placeOrder(
                productId = productId,
                quantity = 1,
                issuedCouponId = issuedCouponId,
            )
            val savedStock = productStockJpaRepository.findById(productId).orElseThrow()

            assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
            assertThat(savedStock.leftStock).isEqualTo(5)
        }

        @Test
        fun `최소_주문금액에_미달하면_409_CONFLICT를_반환하고_재고를_차감하지_않는다`() {
            userService.signUp(사용자_회원가입())
            val productId = registerProduct(price = 10_000, initialStock = 5)
            val issuedCouponId = issueCoupon(createRateTemplate(value = 10, minOrderAmount = 100_000))

            val response = placeOrder(
                productId = productId,
                quantity = 1,
                issuedCouponId = issuedCouponId,
            )
            val savedStock = productStockJpaRepository.findById(productId).orElseThrow()

            assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
            assertThat(savedStock.leftStock).isEqualTo(5)
        }

        @Test
        fun `존재하지_않는_쿠폰으로_주문하면_404_NOT_FOUND를_반환하고_재고를_차감하지_않는다`() {
            userService.signUp(사용자_회원가입())
            val productId = registerProduct(price = 10_000, initialStock = 5)

            val response = placeOrder(
                productId = productId,
                quantity = 1,
                issuedCouponId = 999_999L,
            )
            val savedStock = productStockJpaRepository.findById(productId).orElseThrow()

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
            assertThat(savedStock.leftStock).isEqualTo(5)
        }

        @Test
        fun `재고가_부족하면_주문이_실패하고_쿠폰_사용과_재고_차감이_모두_롤백된다`() {
            userService.signUp(사용자_회원가입())
            val productId = registerProduct(price = 10_000, initialStock = 1)
            val issuedCouponId = issueCoupon(createRateTemplate(value = 10, minOrderAmount = 0))

            val response = placeOrder(
                productId = productId,
                quantity = 2,
                issuedCouponId = issuedCouponId,
            )
            val myCouponsResponse = findMyCoupons()
            val savedStock = productStockJpaRepository.findById(productId).orElseThrow()

            assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
            assertThat(myCouponsResponse.body?.data?.first()?.get("displayStatus")).isEqualTo("AVAILABLE")
            assertThat(savedStock.leftStock).isEqualTo(1)
        }

        private fun expireTemplate(templateId: Long) {
            couponService.updateTemplate(
                templateId = templateId,
                command = CouponTemplateCommand(
                    name = "EXPIRED_RATE",
                    type = "RATE",
                    value = 10,
                    minOrderAmount = 0,
                    expiredAt = LocalDateTime.now().minusDays(1),
                ),
            )
        }

        private fun registerProduct(price: Long, initialStock: Long): Long {
            val brand = brandService.register(브랜드_등록_커맨드())
            return productFacade.registerProduct(
                상품_등록_커맨드(
                    brandId = brand.id,
                    price = price,
                    initialStock = initialStock,
                ),
            ).id
        }

        private fun createRateTemplate(value: Long, minOrderAmount: Long): Long {
            val response = testRestTemplate.exchange(
                "/api-admin/v1/coupons",
                HttpMethod.POST,
                HttpEntity(
                    mapOf(
                        "name" to "ORDER_RATE_$value",
                        "type" to "RATE",
                        "value" to value,
                        "minOrderAmount" to minOrderAmount,
                        "expiredAt" to LocalDateTime.now().plusDays(7).toString(),
                    ),
                    adminHeaders(),
                ),
                mapResponseType,
            )
            return response.body?.data?.number("id") ?: error("쿠폰 템플릿 생성 실패")
        }

        private fun issueCoupon(templateId: Long): Long {
            val response = testRestTemplate.exchange(
                "/api/v1/coupons/$templateId/issue",
                HttpMethod.POST,
                HttpEntity<Unit>(authHeaders()),
                mapResponseType,
            )
            assertThat(response.statusCode).isEqualTo(HttpStatus.ACCEPTED)
            val requestId = UUID.fromString(response.body?.data?.get("requestId").toString())

            couponIssueRequestWorker.process(
                eventId = UUID.randomUUID(),
                consumerGroup = "commerce-api-coupon-issue",
                eventType = "COUPON_ISSUE_REQUESTED_V1",
                requestId = requestId,
            )
            val statusResponse = findIssueRequest(requestId)

            assertThat(statusResponse.body?.data?.get("status")).isEqualTo("ISSUED")
            return statusResponse.body?.data?.number("issuedCouponId") ?: error("쿠폰 발급 실패")
        }

        private fun findIssueRequest(requestId: UUID) =
            testRestTemplate.exchange(
                "/api/v1/coupons/issue-requests/$requestId",
                HttpMethod.GET,
                HttpEntity<Unit>(authHeaders()),
                mapResponseType,
            )

        private fun placeOrder(productId: Long, quantity: Long, issuedCouponId: Long) =
            testRestTemplate.exchange(
                "/api/v1/orders",
                HttpMethod.POST,
                HttpEntity(
                    mapOf(
                        "couponId" to issuedCouponId,
                        "items" to listOf(
                            mapOf(
                                "productId" to productId,
                                "quantity" to quantity,
                            ),
                        ),
                    ),
                    authHeaders(),
                ),
                mapResponseType,
            )

        private fun findMyCoupons() =
            testRestTemplate.exchange(
                "/api/v1/users/me/coupons",
                HttpMethod.GET,
                HttpEntity<Unit>(authHeaders()),
                listResponseType,
            )

        private fun Map<String, Any?>.number(key: String): Long =
            (get(key) as Number).toLong()

        private fun authHeaders(): HttpHeaders {
            val headers = HttpHeaders()
            headers.set("X-Loopers-LoginId", 기본_로그인_ID)
            headers.set("X-Loopers-LoginPw", 기본_비밀번호)
            return headers
        }

        private fun adminHeaders(): HttpHeaders {
            val headers = HttpHeaders()
            headers.set("X-Loopers-Ldap", "admin")
            return headers
        }
    }
