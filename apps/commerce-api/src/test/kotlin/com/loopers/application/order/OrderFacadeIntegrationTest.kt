package com.loopers.application.order

import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponStatus
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.UserCouponModel
import com.loopers.domain.product.Level
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.TechCategory
import com.loopers.domain.stock.StockModel
import com.loopers.fixture.UserModelFixture
import com.loopers.infrastructure.coupon.CouponJpaRepository
import com.loopers.infrastructure.coupon.UserCouponJpaRepository
import com.loopers.infrastructure.order.OrderJpaRepository
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.infrastructure.stock.StockJpaRepository
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.ZonedDateTime

@SpringBootTest
class OrderFacadeIntegrationTest @Autowired constructor(
    private val orderFacade: OrderFacade,
    private val userJpaRepository: UserJpaRepository,
    private val stockJpaRepository: StockJpaRepository,
    private val userCouponJpaRepository: UserCouponJpaRepository,
    private val orderJpaRepository: OrderJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val couponJpaRepository: CouponJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() = databaseCleanUp.truncateAllTables()

    private fun saveUser() = userJpaRepository.save(UserModelFixture.defaults().toModel())

    private var productSequence = 0

    private fun saveProductWithStock(price: Double, quantity: Int): ProductModel {
        val product = productJpaRepository.save(
            ProductModel.of(
                brandId = 1L,
                isbn = "isbn-${++productSequence}",
                name = "테스트 상품 $productSequence",
                authName = "저자",
                techCategory = TechCategory.BACKEND,
                level = Level.BEGINNER,
                price = price,
                description = "설명",
            ),
        )
        stockJpaRepository.save(StockModel.of(product.id, quantity))
        return product
    }

    private fun saveCoupon(minOrderAmount: Double = 10000.0) =
        couponJpaRepository.save(CouponModel.of("쿠폰", CouponType.FIXED, 3000.0, minOrderAmount, ZonedDateTime.now().plusDays(1), 100))

    private fun saveUserCoupon(coupon: CouponModel, userId: Long, used: Boolean = false) =
        userCouponJpaRepository.save(UserCouponModel.of(coupon, userId).apply { if (used) use() })

    @DisplayName("쿠폰을 가지고 주문하면 할인이 적용된 주문이 생성되고, 쿠폰은 USED가 되며, 재고가 차감된다")
    @Test
    fun placeOrderWithCoupon() {
        // given
        val savedUser = saveUser()
        val product = saveProductWithStock(10000.0, 10)
        val userCouponId = saveUserCoupon(saveCoupon(), savedUser.id).id
        
        // when
        val result = orderFacade.placeOrder(savedUser.loginId, listOf(product.id to 2), userCouponId)
        
        // then
        val order = orderJpaRepository.findById(result.orderId).get()
        assertAll(
            { assertThat(order.couponId).isEqualTo(userCouponId) },
            { assertThat(order.discountAmount).isEqualTo(3000.0) },
            { assertThat(order.finalAmount).isEqualTo(17000.0) },
            { assertThat(userCouponJpaRepository.findById(userCouponId).get().status).isEqualTo(CouponStatus.USED) },
        )
    }

    @DisplayName("쿠폰 없이 주문하면 할인 없이(discountAmount=0, couponId=null) 주문이 생성된다")
    @Test
    fun placeOrderWithoutCoupon() {
        // given
        val savedUser = saveUser()
        val product = saveProductWithStock(10000.0, 10)

        // when
        val result = orderFacade.placeOrder(savedUser.loginId, listOf(product.id to 2))

        // then
        val order = orderJpaRepository.findById(result.orderId).get()
        assertAll(
            { assertThat(order.discountAmount).isEqualTo(0.0) },
            { assertThat(order.couponId).isNull() },
            { assertThat(order.finalAmount).isEqualTo(20000.0) },
        )
    }

    @DisplayName("이미 사용한 쿠폰으로 주문하면 예외가 발생하고, 재고는 차감되지 않고 주문도 생성되지 않는다")
    @Test
    fun placeOrderWithUsedCouponFails() {
        // given
        val savedUser = saveUser()
        val product = saveProductWithStock(10000.0, 10)
        val userCoupon = saveUserCoupon(saveCoupon(), savedUser.id, used = true)

        // when then
        assertThatThrownBy { orderFacade.placeOrder(savedUser.loginId, listOf(product.id to 2), userCoupon.id) }
            .isInstanceOf(CoreException::class.java)

        assertAll(
            { assertThat(stockJpaRepository.findStockByProductId(product.id)!!.quantity).isEqualTo(10) },
            { assertThat(orderJpaRepository.count()).isEqualTo(0) },
        )
    }

    @DisplayName("쿠폰은 유효하지만 재고가 부족하면 예외가 발생하고, 이미 USED로 변했던 쿠폰이 AVAILABLE로 롤백된다")
    @Test
    fun placeOrderRollsBackCouponWhenStockShortage() {
        // given
        val savedUser = saveUser()
        val product = saveProductWithStock(10000.0, 1)
        val userCoupon = saveUserCoupon(saveCoupon(), savedUser.id)

        // when then
        assertThatThrownBy { orderFacade.placeOrder(savedUser.loginId, listOf(product.id to 2), userCoupon.id) }
            .isInstanceOf(CoreException::class.java)

        assertAll(
            { assertThat(userCouponJpaRepository.findById(userCoupon.id).get().status).isEqualTo(CouponStatus.AVAILABLE) },
            { assertThat(stockJpaRepository.findStockByProductId(product.id)!!.quantity).isEqualTo(1) },
            { assertThat(orderJpaRepository.count()).isEqualTo(0) },
        )
    }
}
