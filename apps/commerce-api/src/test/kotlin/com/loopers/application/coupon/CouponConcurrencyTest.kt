package com.loopers.application.coupon

import com.loopers.application.coupon.usecase.IssueCouponUsecase
import com.loopers.application.order.OrderCommand
import com.loopers.application.order.usecase.CreateOrderUsecase
import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.domain.coupon.UserCouponStatus
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductStockModel
import com.loopers.domain.product.ProductStockRepository
import com.loopers.domain.user.UserService
import com.loopers.support.runConcurrently
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZonedDateTime

@SpringBootTest
class CouponConcurrencyTest @Autowired constructor(
    private val createOrderUsecase: CreateOrderUsecase,
    private val issueCouponUsecase: IssueCouponUsecase,
    private val userService: UserService,
    private val productRepository: ProductRepository,
    private val productStockRepository: ProductStockRepository,
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("동일한 쿠폰으로 여러 기기에서 동시에 주문해도 쿠폰은 단 한 번만 사용된다.")
    @Test
    fun usesCouponExactlyOnce_whenOrderedConcurrently() {
        // arrange
        val threadCount = 5
        val user = signUp("buyer")
        val product = productRepository.save(
            ProductModel(brandId = 1L, name = "상품", description = "설명", price = BigDecimal("10000")),
        )
        productStockRepository.save(ProductStockModel(productId = product.id, quantity = 100))
        val coupon = saveCoupon()
        val userCouponId = issueCouponUsecase.execute(
            IssueCouponCommand(loginId = "buyer", password = PASSWORD, couponId = coupon.id),
        ).id

        // act
        val errors = runConcurrently(threadCount) {
            createOrderUsecase.execute(
                OrderCommand(
                    loginId = "buyer",
                    password = PASSWORD,
                    items = listOf(OrderCommand.OrderItemCommand(productId = product.id, quantity = 1)),
                    couponId = userCouponId,
                ),
            )
        }

        // assert
        val successCount = threadCount - errors.size
        assertAll(
            { assertThat(successCount).isEqualTo(1) },
            { assertThat(userCouponRepository.findByIdAndUserId(userCouponId, user.id)!!.status).isEqualTo(UserCouponStatus.USED) },
            { assertThat(productStockRepository.findByProductId(product.id)!!.quantity).isEqualTo(99) },
        )
    }

    @DisplayName("동일 사용자가 같은 쿠폰을 동시에 발급 요청해도 한 장만 발급된다.")
    @Test
    fun issuesCouponExactlyOnce_whenRequestedConcurrently() {
        // arrange
        val threadCount = 5
        val user = signUp("issuer")
        val coupon = saveCoupon()

        // act
        val errors = runConcurrently(threadCount) {
            issueCouponUsecase.execute(IssueCouponCommand(loginId = "issuer", password = PASSWORD, couponId = coupon.id))
        }

        // assert
        assertAll(
            { assertThat(threadCount - errors.size).isEqualTo(1) },
            { assertThat(userCouponRepository.findAllByUserId(user.id)).hasSize(1) },
        )
    }

    private fun signUp(loginId: String) = userService.signUp(
        UserService.SignUpCommand(
            loginId = loginId,
            password = PASSWORD,
            name = "테스터",
            birthDate = LocalDate.of(1990, 1, 1),
            email = "$loginId@loopers.com",
        ),
    )

    private fun saveCoupon() = couponRepository.save(
        CouponModel(
            name = "쿠폰",
            type = CouponType.FIXED,
            discountValue = BigDecimal("1000"),
            minOrderAmount = null,
            expiredAt = ZonedDateTime.now().plusDays(30),
        ),
    )

    companion object {
        private const val PASSWORD = "Password1!"
    }
}
