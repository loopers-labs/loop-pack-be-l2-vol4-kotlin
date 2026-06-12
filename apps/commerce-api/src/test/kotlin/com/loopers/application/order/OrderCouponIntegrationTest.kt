package com.loopers.application.order

import com.loopers.application.coupon.IssueCouponCommand
import com.loopers.application.coupon.usecase.IssueCouponUsecase
import com.loopers.application.order.usecase.CreateOrderUsecase
import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.UserCouponModel
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.domain.coupon.UserCouponStatus
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductStockModel
import com.loopers.domain.product.ProductStockRepository
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZonedDateTime

@SpringBootTest
class OrderCouponIntegrationTest @Autowired constructor(
    private val createOrderUsecase: CreateOrderUsecase,
    private val issueCouponUsecase: IssueCouponUsecase,
    private val userService: UserService,
    private val productRepository: ProductRepository,
    private val productStockRepository: ProductStockRepository,
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private var productId = 0L
    private var userId = 0L

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
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("쿠폰 주문 성공 시 재고 차감, 쿠폰 사용, 금액 스냅샷이 모두 반영된다.")
    @Test
    fun commitsAllChanges_whenOrderSucceeds() {
        // arrange
        val userCouponId = issueCoupon(BigDecimal("3000.00"))

        // act
        val order = createOrderUsecase.execute(command(quantity = 2, couponId = userCouponId))

        // assert
        assertAll(
            { assertThat(order.totalPrice).isEqualByComparingTo(BigDecimal("20000.00")) },
            { assertThat(order.discountAmount).isEqualByComparingTo(BigDecimal("3000.00")) },
            { assertThat(order.paidPrice).isEqualByComparingTo(BigDecimal("17000.00")) },
            { assertThat(productStockRepository.findByProductId(productId)!!.quantity).isEqualTo(8) },
            { assertThat(userCouponRepository.findByIdAndUserId(userCouponId, userId)!!.status).isEqualTo(UserCouponStatus.USED) },
        )
    }

    @DisplayName("재고가 부족하면 주문이 실패하고 쿠폰은 사용되지 않은 채 남는다.")
    @Test
    fun rollsBackCoupon_whenStockIsInsufficient() {
        // arrange
        val userCouponId = issueCoupon(BigDecimal("3000.00"))

        // act
        val exception = assertThrows<CoreException> {
            createOrderUsecase.execute(command(quantity = 999, couponId = userCouponId))
        }

        // assert
        assertAll(
            { assertThat(exception.errorType).isEqualTo(ErrorType.CONFLICT) },
            { assertThat(productStockRepository.findByProductId(productId)!!.quantity).isEqualTo(10) },
            { assertThat(userCouponRepository.findByIdAndUserId(userCouponId, userId)!!.status).isEqualTo(UserCouponStatus.AVAILABLE) },
        )
    }

    @DisplayName("이미 사용한 쿠폰으로 주문하면 실패하고 재고는 차감되지 않는다.")
    @Test
    fun rollsBackStock_whenCouponAlreadyUsed() {
        // arrange
        val userCouponId = issueCoupon(BigDecimal("3000.00"))
        createOrderUsecase.execute(command(quantity = 1, couponId = userCouponId))

        // act
        val exception = assertThrows<CoreException> {
            createOrderUsecase.execute(command(quantity = 1, couponId = userCouponId))
        }

        // assert
        assertAll(
            { assertThat(exception.errorType).isEqualTo(ErrorType.CONFLICT) },
            { assertThat(productStockRepository.findByProductId(productId)!!.quantity).isEqualTo(9) },
        )
    }

    @DisplayName("만료된 쿠폰으로 주문하면 실패하고 재고는 차감되지 않는다.")
    @Test
    fun rollsBackStock_whenCouponIsExpired() {
        // arrange
        val userCouponId = saveExpiredUserCoupon()

        // act
        val exception = assertThrows<CoreException> {
            createOrderUsecase.execute(command(quantity = 1, couponId = userCouponId))
        }

        // assert
        assertAll(
            { assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST) },
            { assertThat(productStockRepository.findByProductId(productId)!!.quantity).isEqualTo(10) },
            { assertThat(userCouponRepository.findByIdAndUserId(userCouponId, userId)!!.status).isEqualTo(UserCouponStatus.AVAILABLE) },
        )
    }

    @DisplayName("존재하지 않는 쿠폰으로 주문하면 NOT_FOUND로 실패하고 재고는 차감되지 않는다.")
    @Test
    fun failsOrder_whenCouponDoesNotExist() {
        // act
        val exception = assertThrows<CoreException> {
            createOrderUsecase.execute(command(quantity = 1, couponId = 99999L))
        }

        // assert
        assertAll(
            { assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND) },
            { assertThat(productStockRepository.findByProductId(productId)!!.quantity).isEqualTo(10) },
        )
    }

    private fun issueCoupon(discountValue: BigDecimal): Long {
        val coupon = couponRepository.save(coupon(discountValue = discountValue, expiredAt = ZonedDateTime.now().plusDays(30)))
        return issueCouponUsecase.execute(IssueCouponCommand(loginId = LOGIN_ID, password = PASSWORD, couponId = coupon.id)).id
    }

    private fun saveExpiredUserCoupon(): Long {
        val coupon = couponRepository.save(coupon(discountValue = BigDecimal("3000.00"), expiredAt = ZonedDateTime.now().minusDays(1)))
        return userCouponRepository.save(UserCouponModel(userId = userId, couponId = coupon.id)).id
    }

    private fun coupon(discountValue: BigDecimal, expiredAt: ZonedDateTime): CouponModel {
        return CouponModel(
            name = "쿠폰",
            type = CouponType.FIXED,
            discountValue = discountValue,
            minOrderAmount = null,
            expiredAt = expiredAt,
        )
    }

    private fun command(quantity: Int, couponId: Long?) = OrderCommand(
        loginId = LOGIN_ID,
        password = PASSWORD,
        items = listOf(OrderCommand.OrderItemCommand(productId = productId, quantity = quantity)),
        couponId = couponId,
    )

    companion object {
        private const val LOGIN_ID = "tester"
        private const val PASSWORD = "Password1!"
    }
}
