package com.loopers.order.application

import com.loopers.coupon.domain.Coupon
import com.loopers.coupon.domain.CouponErrorCode
import com.loopers.coupon.domain.CouponRepository
import com.loopers.coupon.domain.CouponType
import com.loopers.coupon.domain.UserCoupon
import com.loopers.coupon.domain.UserCouponGrantedType
import com.loopers.coupon.domain.UserCouponRepository
import com.loopers.coupon.domain.UserCouponStatus
import com.loopers.inventory.domain.Inventory
import com.loopers.inventory.domain.InventoryErrorCode
import com.loopers.inventory.domain.InventoryRepository
import com.loopers.product.domain.Product
import com.loopers.product.domain.ProductName
import com.loopers.product.domain.ProductRepository
import com.loopers.shared.domain.Money
import com.loopers.support.DatabaseCleanup
import com.loopers.support.error.ConflictException
import com.loopers.support.runConcurrently
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime

@SpringBootTest
@ActiveProfiles("test")
class OrderConcurrencyIntegrationTest @Autowired constructor(
    private val orderFacade: OrderFacade,
    private val productRepository: ProductRepository,
    private val inventoryRepository: InventoryRepository,
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
    private val databaseCleanup: DatabaseCleanup,
) {
    @BeforeEach
    fun setUp() {
        databaseCleanup.execute()
    }

    @DisplayName("동일한 쿠폰으로 동시에 주문해도, 쿠폰은 단 한 번만 사용되고 주문도 1건만 성공한다.")
    @Test
    fun usesCouponOnlyOnce_whenSameCouponOrderedConcurrently() {
        val product = productRepository.save(Product(brandId = 1L, name = ProductName("에어맥스"), price = Money(10_000)))
        inventoryRepository.save(Inventory.createFor(product.id, 100))
        val coupon = couponRepository.save(
            Coupon(
                type = CouponType.FIXED,
                name = "천원 할인",
                value = 1_000,
                minOrderAmount = Money(0),
                expiredAt = LocalDateTime.now().plusDays(7),
                createdBy = 99L,
            ),
        )
        userCouponRepository.save(
            UserCoupon(userId = 1L, couponId = coupon.id, grantedType = UserCouponGrantedType.ADMIN, grantedBy = 99L),
        )

        val failures = runConcurrently(threadCount = 10) {
            orderFacade.place(
                command = OrderCreateCommand(
                    userId = 1L,
                    items = listOf(OrderLineCommand(productId = product.id, quantity = 1, price = 10_000)),
                    couponId = coupon.id,
                    expectedOriginalAmount = 10_000,
                    expectedDiscountAmount = 1_000,
                ),
            )
        }

        val userCoupon = userCouponRepository.findByUserIdAndCouponId(1L, coupon.id)!!
        assertAll(
            { assertThat(failures).hasSize(9) },
            {
                assertThat(failures).allMatch {
                    it is OptimisticLockingFailureException ||
                        (it is ConflictException && it.errorCode == CouponErrorCode.ALREADY_USED)
                }
            },
            { assertThat(userCoupon.status).isEqualTo(UserCouponStatus.USED) },
            { assertThat(inventoryRepository.findByProductId(product.id)!!.quantity).isEqualTo(99) },
        )
    }

    @DisplayName("동일한 상품에 재고보다 많은 주문이 동시에 요청되어도, 재고만큼만 성공하고 초과 판매되지 않는다.")
    @Test
    fun decreasesStockExactly_whenSameProductOrderedConcurrently() {
        val product = productRepository.save(Product(brandId = 1L, name = ProductName("에어맥스"), price = Money(10_000)))
        inventoryRepository.save(Inventory.createFor(product.id, 5))

        val failures = runConcurrently(threadCount = 10) { index ->
            orderFacade.place(
                command = OrderCreateCommand(
                    userId = (index + 1).toLong(),
                    items = listOf(OrderLineCommand(productId = product.id, quantity = 1, price = 10_000)),
                    expectedOriginalAmount = 10_000,
                    expectedDiscountAmount = 0,
                ),
            )
        }

        assertAll(
            { assertThat(failures).hasSize(5) },
            {
                assertThat(failures).allMatch {
                    it is ConflictException && it.errorCode == InventoryErrorCode.STOCK_INSUFFICIENT
                }
            },
            { assertThat(inventoryRepository.findByProductId(product.id)!!.quantity).isEqualTo(0) },
        )
    }
}
