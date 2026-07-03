package com.loopers.application.order

import com.loopers.application.stock.StockApplicationService
import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.DiscountPolicy
import com.loopers.domain.coupon.UserCoupon
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.user.EncodedPassword
import com.loopers.infrastructure.coupon.UserCouponJpaRepository
import com.loopers.infrastructure.product.ProductJpaEntity
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.infrastructure.stock.StockJpaEntity
import com.loopers.infrastructure.stock.StockJpaRepository
import com.loopers.infrastructure.user.UserJpaEntity
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate

@SpringBootTest
class OrderFacadeIntegrationTest @Autowired constructor(
    private val orderFacade: OrderFacade,
    private val stockApplicationService: StockApplicationService,
    private val productJpaRepository: ProductJpaRepository,
    private val stockJpaRepository: StockJpaRepository,
    private val userJpaRepository: UserJpaRepository,
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
    private val userCouponJpaRepository: UserCouponJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("주문 생성 시, ")
    @Nested
    inner class PlaceOrder {
        @DisplayName("재고 차감과 쿠폰 사용 후 PENDING_PAYMENT 상태의 주문을 생성한다.")
        @Test
        fun placeOrder_createsOrderWithPendingPaymentStatus() {
            // arrange
            val user = userJpaRepository.save(newUserJpaEntity())
            val product = saveProductWithStock(price = 10_000L, stock = 10)
            val coupon = couponRepository.save(
                Coupon(name = "1000원 할인", policy = DiscountPolicy.FixedAmount(1_000L)),
            )
            val userCoupon = userCouponRepository.save(
                UserCoupon(userId = user.id, couponId = coupon.id!!),
            )

            // act
            val order = orderFacade.placeOrder(
                CreateOrderCommand(
                    userId = user.id,
                    items = listOf(CreateOrderItemCommand(productId = product.id, quantity = 1)),
                    userCouponId = userCoupon.id,
                ),
            )

            // assert
            val usedCoupon = userCouponJpaRepository.findByIdAndDeletedAtIsNull(userCoupon.id!!)
            val remainingStock = stockApplicationService.getStock(product.id)
            assertAll(
                { assertThat(order.status).isEqualTo(OrderStatus.PENDING_PAYMENT) },
                { assertThat(order.paymentAmount).isEqualTo(9_000L) },
                { assertThat(usedCoupon?.usedAt).isNotNull() },
                { assertThat(remainingStock.quantity).isEqualTo(9) },
            )
        }
    }

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

    private fun newUserJpaEntity(
        loginId: String = "seondays",
        password: String = "\$2a\$10\$existingHashedPassword.",
        name: String = "선데이",
        birthDate: LocalDate = LocalDate.of(1990, 1, 1),
        email: String = "seondays@example.com",
    ) = UserJpaEntity(
        loginId = loginId,
        encodedPassword = EncodedPassword(password),
        name = name,
        birthDate = birthDate,
        email = email,
    )
}
