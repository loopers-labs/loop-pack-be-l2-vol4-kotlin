package com.loopers.application.order

import com.loopers.application.product.CreateProductCommand
import com.loopers.application.user.SignupCommand
import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandRepositoryPort
import com.loopers.domain.common.PageRequest
import com.loopers.domain.coupon.CouponStatus
import com.loopers.domain.coupon.CouponTemplate
import com.loopers.domain.coupon.CouponTemplateRepositoryPort
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.UserCoupon
import com.loopers.domain.coupon.UserCouponRepositoryPort
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.stock.StockRepositoryPort
import com.loopers.interfaces.api.order.OrderAdminApplicationServicePort
import com.loopers.interfaces.api.order.OrderApplicationServicePort
import com.loopers.interfaces.api.product.ProductAdminApplicationServicePort
import com.loopers.interfaces.api.user.UserApplicationServicePort
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime

@SpringBootTest
class OrderApplicationServiceIntegrationTest @Autowired constructor(
    private val orderApplicationService: OrderApplicationServicePort,
    private val orderAdminApplicationService: OrderAdminApplicationServicePort,
    private val userApplicationService: UserApplicationServicePort,
    private val productAdminApplicationService: ProductAdminApplicationServicePort,
    private val brandRepositoryPort: BrandRepositoryPort,
    private val stockRepositoryPort: StockRepositoryPort,
    private val couponTemplateRepositoryPort: CouponTemplateRepositoryPort,
    private val userCouponRepositoryPort: UserCouponRepositoryPort,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun signup(loginId: String = "tester01", pw: String = "password1234"): Long =
        userApplicationService.signup(
            SignupCommand(
                loginId = loginId,
                rawPassword = pw,
                name = "테스터",
                birth = LocalDate.of(2000, 1, 1),
                email = "$loginId@example.com",
            ),
        ).id

    private fun saveBrand(name: String = "Nike-${System.nanoTime()}"): Long =
        brandRepositoryPort.save(Brand.create(name = name, description = "x")).id

    private fun saveProduct(brandId: Long, name: String = "p", price: Long = 1_000L, quantity: Int = 10): Long =
        productAdminApplicationService.createProduct(
            CreateProductCommand(name = name, price = price, description = "d", brandId = brandId, quantity = quantity),
        ).id

    private fun saveCouponTemplate(
        type: CouponType = CouponType.FIXED,
        value: Long = 5_000L,
        minOrderAmount: Long = 0L,
        expiredAt: LocalDateTime = LocalDateTime.now().plusDays(1),
    ): Long = couponTemplateRepositoryPort.save(
        CouponTemplate.create(
            name = "테스트 쿠폰",
            type = type,
            value = value,
            minOrderAmount = minOrderAmount,
            expiredAt = expiredAt,
        ),
    ).id

    private fun issueCoupon(templateId: Long, userId: Long): Long =
        userCouponRepositoryPort.save(
            UserCoupon.issue(couponTemplateId = templateId, userId = userId, issuedAt = LocalDateTime.now()),
        ).id

    @DisplayName("createOrder")
    @Nested
    inner class CreateOrder {
        @DisplayName("주문 생성 시 CREATED 로 확정되고 재고가 선점(차감)된다.")
        @Test
        fun placesCreatedOrderAndDecreasesStock() {
            val userId = signup()
            val brandId = saveBrand()
            val p1 = saveProduct(brandId = brandId, name = "p1", price = 1_000L, quantity = 5)
            val p2 = saveProduct(brandId = brandId, name = "p2", price = 2_000L, quantity = 3)

            val detail = orderApplicationService.createOrder(
                CreateOrderCommand(
                    userId = userId,
                    items = listOf(
                        CreateOrderItemCommand(productId = p1, quantity = 2),
                        CreateOrderItemCommand(productId = p2, quantity = 1),
                    ),
                ),
            )

            assertThat(detail.status).isEqualTo(OrderStatus.CREATED)
            assertThat(detail.totalAmount).isEqualTo(4_000L)
            assertThat(stockRepositoryPort.findByProductId(p1)?.quantity).isEqualTo(3)
            assertThat(stockRepositoryPort.findByProductId(p2)?.quantity).isEqualTo(2)
        }

        @DisplayName("스냅샷에 상품명/가격/브랜드명이 주문 시점 값으로 동결된다.")
        @Test
        fun freezesSnapshot() {
            val userId = signup()
            val brandId = saveBrand("Nike-original")
            val p1 = saveProduct(brandId = brandId, name = "에어맥스", price = 100_000L, quantity = 5)

            val detail = orderApplicationService.createOrder(
                CreateOrderCommand(userId = userId, items = listOf(CreateOrderItemCommand(p1, 1))),
            )

            assertThat(detail.items).hasSize(1)
            assertThat(detail.items.first().snapshotProductName).isEqualTo("에어맥스")
            assertThat(detail.items.first().snapshotPrice).isEqualTo(100_000L)
            assertThat(detail.items.first().snapshotBrandName).isEqualTo("Nike-original")
        }

        @DisplayName("재고 부족 시 도메인 예외가 발생하고 트랜잭션 롤백으로 어떤 재고도 차감되지 않는다.")
        @Test
        fun rollbacksWhenStockInsufficient() {
            val userId = signup()
            val brandId = saveBrand()
            val plenty = saveProduct(brandId = brandId, name = "plenty", price = 1_000L, quantity = 10)
            val scarce = saveProduct(brandId = brandId, name = "scarce", price = 2_000L, quantity = 1)

            assertThrows<IllegalArgumentException> {
                orderApplicationService.createOrder(
                    CreateOrderCommand(
                        userId = userId,
                        items = listOf(
                            CreateOrderItemCommand(plenty, 5),
                            CreateOrderItemCommand(scarce, 3),
                        ),
                    ),
                )
            }

            assertThat(stockRepositoryPort.findByProductId(plenty)?.quantity).isEqualTo(10)
            assertThat(stockRepositoryPort.findByProductId(scarce)?.quantity).isEqualTo(1)
        }

        @DisplayName("없는 상품 ID로 주문하면 NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenProductMissing() {
            val userId = signup()

            val result = assertThrows<CoreException> {
                orderApplicationService.createOrder(
                    CreateOrderCommand(userId = userId, items = listOf(CreateOrderItemCommand(99999L, 1))),
                )
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("items 가 비어 있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenEmptyItems() {
            val userId = signup()

            val result = assertThrows<CoreException> {
                orderApplicationService.createOrder(CreateOrderCommand(userId = userId, items = emptyList()))
            }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("createOrder + 쿠폰")
    @Nested
    inner class CreateOrderWithCoupon {
        @DisplayName("쿠폰 적용 주문이 성공하면 쿠폰이 USED 로 전이되고 금액 스냅샷(할인/최종)이 기록된다.")
        @Test
        fun appliesCouponAndMarksUsed() {
            val userId = signup()
            val brandId = saveBrand()
            val p1 = saveProduct(brandId = brandId, name = "p1", price = 10_000L, quantity = 5)
            val templateId = saveCouponTemplate(type = CouponType.FIXED, value = 5_000L)
            val couponId = issueCoupon(templateId, userId)

            val detail = orderApplicationService.createOrder(
                CreateOrderCommand(userId = userId, items = listOf(CreateOrderItemCommand(p1, 1)), couponId = couponId),
            )

            assertThat(detail.status).isEqualTo(OrderStatus.CREATED)
            assertThat(detail.totalAmount).isEqualTo(10_000L)
            assertThat(detail.discountAmount).isEqualTo(5_000L)
            assertThat(detail.actualAmount).isEqualTo(5_000L)
            assertThat(detail.appliedCoupon?.issuedCouponId).isEqualTo(couponId)
            assertThat(detail.appliedCoupon?.couponType).isEqualTo("FIXED")
            assertThat(userCouponRepositoryPort.findById(couponId)?.status).isEqualTo(CouponStatus.USED)
        }

        @DisplayName("정률 쿠폰은 주문 금액 비율로 할인된다.")
        @Test
        fun appliesRateCoupon() {
            val userId = signup()
            val brandId = saveBrand()
            val p1 = saveProduct(brandId = brandId, name = "p1", price = 10_000L, quantity = 5)
            val templateId = saveCouponTemplate(type = CouponType.RATE, value = 10L)
            val couponId = issueCoupon(templateId, userId)

            val detail = orderApplicationService.createOrder(
                CreateOrderCommand(userId = userId, items = listOf(CreateOrderItemCommand(p1, 2)), couponId = couponId),
            )

            assertThat(detail.totalAmount).isEqualTo(20_000L)
            assertThat(detail.discountAmount).isEqualTo(2_000L)
            assertThat(detail.actualAmount).isEqualTo(18_000L)
        }

        @DisplayName("타 유저 소유 쿠폰으로 주문하면 FORBIDDEN 이고 재고/쿠폰은 그대로다(롤백).")
        @Test
        fun throwsForbidden_whenOtherUserCoupon() {
            val owner = signup(loginId = "owner01")
            val stranger = signup(loginId = "stranger01")
            val brandId = saveBrand()
            val p1 = saveProduct(brandId = brandId, name = "p1", price = 10_000L, quantity = 5)
            val templateId = saveCouponTemplate()
            val ownerCoupon = issueCoupon(templateId, owner)

            val result = assertThrows<CoreException> {
                orderApplicationService.createOrder(
                    CreateOrderCommand(userId = stranger, items = listOf(CreateOrderItemCommand(p1, 1)), couponId = ownerCoupon),
                )
            }

            assertThat(result.errorType).isEqualTo(ErrorType.FORBIDDEN)
            assertThat(userCouponRepositoryPort.findById(ownerCoupon)?.status).isEqualTo(CouponStatus.AVAILABLE)
            assertThat(stockRepositoryPort.findByProductId(p1)?.quantity).isEqualTo(5)
        }

        @DisplayName("이미 사용된 쿠폰으로 주문하면 BAD_REQUEST 이다.")
        @Test
        fun throwsBadRequest_whenAlreadyUsed() {
            val userId = signup()
            val brandId = saveBrand()
            val p1 = saveProduct(brandId = brandId, name = "p1", price = 10_000L, quantity = 5)
            val templateId = saveCouponTemplate()
            val couponId = issueCoupon(templateId, userId)
            // 1차 주문으로 쿠폰 소진
            orderApplicationService.createOrder(
                CreateOrderCommand(userId = userId, items = listOf(CreateOrderItemCommand(p1, 1)), couponId = couponId),
            )

            val result = assertThrows<CoreException> {
                orderApplicationService.createOrder(
                    CreateOrderCommand(userId = userId, items = listOf(CreateOrderItemCommand(p1, 1)), couponId = couponId),
                )
            }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("존재하지 않는 쿠폰으로 주문하면 NOT_FOUND 이고 재고는 그대로다(롤백).")
        @Test
        fun throwsNotFound_whenCouponMissing() {
            val userId = signup()
            val brandId = saveBrand()
            val p1 = saveProduct(brandId = brandId, name = "p1", price = 10_000L, quantity = 5)

            val result = assertThrows<CoreException> {
                orderApplicationService.createOrder(
                    CreateOrderCommand(userId = userId, items = listOf(CreateOrderItemCommand(p1, 1)), couponId = 9999L),
                )
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
            assertThat(stockRepositoryPort.findByProductId(p1)?.quantity).isEqualTo(5)
        }

        @DisplayName("최소 주문 금액 미달 쿠폰이면 BAD_REQUEST 이다.")
        @Test
        fun throwsBadRequest_whenBelowMinOrderAmount() {
            val userId = signup()
            val brandId = saveBrand()
            val p1 = saveProduct(brandId = brandId, name = "p1", price = 1_000L, quantity = 5)
            val templateId = saveCouponTemplate(minOrderAmount = 50_000L)
            val couponId = issueCoupon(templateId, userId)

            val result = assertThrows<CoreException> {
                orderApplicationService.createOrder(
                    CreateOrderCommand(userId = userId, items = listOf(CreateOrderItemCommand(p1, 1)), couponId = couponId),
                )
            }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("getMyOrders")
    @Nested
    inner class GetMyOrders {
        @DisplayName("본인의 기간 내 주문만 반환한다.")
        @Test
        fun returnsOwnOrdersInPeriod() {
            val me = signup()
            val brandId = saveBrand()
            val p1 = saveProduct(brandId = brandId, name = "p1", price = 1_000L, quantity = 10)
            orderApplicationService.createOrder(CreateOrderCommand(me, listOf(CreateOrderItemCommand(p1, 1))))
            orderApplicationService.createOrder(CreateOrderCommand(me, listOf(CreateOrderItemCommand(p1, 1))))

            val result = orderApplicationService.getMyOrders(
                userId = me,
                startAt = ZonedDateTime.now().minusDays(1),
                endAt = ZonedDateTime.now().plusDays(1),
                pageRequest = PageRequest(page = 0, size = 10),
            )

            assertThat(result.totalElements).isEqualTo(2L)
            assertThat(result.items).hasSize(2)
        }

        @DisplayName("startAt > endAt 면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenStartAfterEnd() {
            val me = signup()

            val result = assertThrows<CoreException> {
                orderApplicationService.getMyOrders(
                    userId = me,
                    startAt = ZonedDateTime.now(),
                    endAt = ZonedDateTime.now().minusDays(1),
                    pageRequest = PageRequest(page = 0, size = 10),
                )
            }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("주문이 없으면 빈 페이지를 반환한다.")
        @Test
        fun returnsEmpty() {
            val me = signup()

            val result = orderApplicationService.getMyOrders(
                userId = me,
                startAt = ZonedDateTime.now().minusDays(1),
                endAt = ZonedDateTime.now().plusDays(1),
                pageRequest = PageRequest(page = 0, size = 10),
            )

            assertThat(result.items).isEmpty()
            assertThat(result.totalElements).isEqualTo(0L)
        }
    }

    @DisplayName("getMyOrder")
    @Nested
    inner class GetMyOrder {
        @DisplayName("본인의 주문 상세를 반환한다.")
        @Test
        fun returnsOwnOrderDetail() {
            val me = signup()
            val brandId = saveBrand()
            val p1 = saveProduct(brandId = brandId, name = "p1", price = 1_000L, quantity = 10)
            val created = orderApplicationService.createOrder(
                CreateOrderCommand(me, listOf(CreateOrderItemCommand(p1, 2))),
            )

            val detail = orderApplicationService.getMyOrder(userId = me, orderId = created.id)

            assertThat(detail.id).isEqualTo(created.id)
            assertThat(detail.totalAmount).isEqualTo(2_000L)
            assertThat(detail.items).hasSize(1)
        }

        @DisplayName("타인의 주문을 조회하면 FORBIDDEN 예외가 발생한다.")
        @Test
        fun throwsForbidden_whenOtherUser() {
            val owner = signup(loginId = "owner01")
            val stranger = signup(loginId = "stranger01")
            val brandId = saveBrand()
            val p1 = saveProduct(brandId = brandId)
            val created = orderApplicationService.createOrder(
                CreateOrderCommand(owner, listOf(CreateOrderItemCommand(p1, 1))),
            )

            val result = assertThrows<CoreException> {
                orderApplicationService.getMyOrder(userId = stranger, orderId = created.id)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.FORBIDDEN)
        }

        @DisplayName("존재하지 않는 orderId 로 조회하면 NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenMissing() {
            val me = signup()

            val result = assertThrows<CoreException> {
                orderApplicationService.getMyOrder(userId = me, orderId = 9999L)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("어드민 getOrders / getOrder")
    @Nested
    inner class AdminQueries {
        @DisplayName("어드민 목록은 loginId 가 포함된 요약을 반환한다.")
        @Test
        fun returnsAdminSummariesWithLoginId() {
            val me = signup(loginId = "buyer01")
            val brandId = saveBrand()
            val p1 = saveProduct(brandId = brandId)
            orderApplicationService.createOrder(CreateOrderCommand(me, listOf(CreateOrderItemCommand(p1, 1))))

            val page = orderAdminApplicationService.getOrders(PageRequest(page = 0, size = 10))

            assertThat(page.totalElements).isEqualTo(1L)
            assertThat(page.items).hasSize(1)
            assertThat(page.items.first().loginId).isEqualTo("buyer01")
        }

        @DisplayName("어드민 상세는 loginId 가 포함된 상세를 반환한다.")
        @Test
        fun returnsAdminDetailWithLoginId() {
            val me = signup(loginId = "buyer02")
            val brandId = saveBrand()
            val p1 = saveProduct(brandId = brandId)
            val created = orderApplicationService.createOrder(
                CreateOrderCommand(me, listOf(CreateOrderItemCommand(p1, 1))),
            )

            val detail = orderAdminApplicationService.getOrder(created.id)

            assertThat(detail.id).isEqualTo(created.id)
            assertThat(detail.loginId).isEqualTo("buyer02")
        }

        @DisplayName("어드민 상세 — 존재하지 않으면 NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenMissing() {
            val result = assertThrows<CoreException> { orderAdminApplicationService.getOrder(9999L) }
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }
}
