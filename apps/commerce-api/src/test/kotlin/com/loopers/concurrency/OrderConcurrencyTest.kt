package com.loopers.concurrency

import com.loopers.application.order.OrderFacade
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
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.ZonedDateTime

@SpringBootTest
class OrderConcurrencyTest @Autowired constructor(
    private val orderFacade: OrderFacade,
    private val userJpaRepository: UserJpaRepository,
    private val stockJpaRepository: StockJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val couponJpaRepository: CouponJpaRepository,
    private val userCouponJpaRepository: UserCouponJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val orderJpaRepository: OrderJpaRepository,
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
    
    @DisplayName("재고 1개인 상품에 여러 주문이 동시에 들어와도 정확히 1건만 성공하고 재고는 0이 된다.")
    @Test
    fun concurrentOrdersSuccessTest() {
        // given
        val user = saveUser()
        val product = saveProductWithStock(price = 10000.0, quantity = 1)
        val threadCount = 10

        // when
        val results = runConcurrently(threadCount) {
            orderFacade.placeOrder(user.loginId, listOf(product.id to 1))
        }
        
        // then
        assertAll(
            { assertThat(results.count { it.isSuccess }).isEqualTo(1) },
            { assertThat(results.count { it.isFailure }).isEqualTo(threadCount - 1) },
            { assertThat(stockJpaRepository.findStockByProductId(product.id)!!.quantity).isEqualTo(0) },
        )
    }

    @DisplayName("같은 쿠폰으로 여러 주문이 동시에 들어와도 쿠폰은 단 한 번만 사용된다")
    @Test
    fun concurrentOrders_useCouponExactlyOnce() {
        // given
        val user = saveUser()
        val product = saveProductWithStock(price = 10000.0, quantity = 100)
        val userCoupon = saveUserCoupon(saveCoupon(), user.id)
        val threadCount = 10

        // when
        val results = runConcurrently(threadCount) {
            orderFacade.placeOrder(user.loginId, listOf(product.id to 1), userCoupon.id)
        }

        // then
        assertAll(
            { assertThat(results.count { it.isSuccess }).isEqualTo(1) },
            { assertThat(results.count { it.isFailure }).isEqualTo(threadCount - 1) },
            { assertThat(userCouponJpaRepository.findById(userCoupon.id).get().status).isEqualTo(CouponStatus.USED) },
            { assertThat(userCouponJpaRepository.findById(userCoupon.id).get().status).isEqualTo(CouponStatus.USED) },
            { assertThat(orderJpaRepository.count()).isEqualTo(1) },        )
    }
}
