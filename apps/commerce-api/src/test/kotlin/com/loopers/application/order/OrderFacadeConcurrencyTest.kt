package com.loopers.application.order

import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.DiscountPolicy
import com.loopers.domain.coupon.UserCoupon
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.user.EncodedPassword
import com.loopers.infrastructure.coupon.UserCouponJpaRepository
import com.loopers.infrastructure.order.OrderJpaRepository
import com.loopers.infrastructure.product.ProductJpaEntity
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.infrastructure.stock.StockJpaEntity
import com.loopers.infrastructure.stock.StockJpaRepository
import com.loopers.infrastructure.user.UserJpaEntity
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
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
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

@SpringBootTest
class OrderFacadeConcurrencyTest @Autowired constructor(
    private val orderFacade: OrderFacade,
    private val orderPrepareService: OrderPrepareService,
    private val orderConfirmService: OrderConfirmService,
    private val orderReleaseService: OrderReleaseService,
    private val userJpaRepository: UserJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val stockJpaRepository: StockJpaRepository,
    private val orderJpaRepository: OrderJpaRepository,
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
    private val userCouponJpaRepository: UserCouponJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("주문 동시성 제어 시, ")
    @Nested
    inner class CreateOrderConcurrently {
        @DisplayName("동일한 발급 쿠폰으로 여러 주문이 동시에 요청되어도 쿠폰은 한 번만 사용된다.")
        @Test
        fun usesCouponOnlyOnce_whenSameUserCouponIsRequestedConcurrently() {
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
            val results = runConcurrently(10) {
                orderFacade.placeOrder(
                    CreateOrderCommand(
                        userId = user.id,
                        items = listOf(CreateOrderItemCommand(productId = product.id, quantity = 1)),
                        userCouponId = userCoupon.id,
                    ),
                )
            }

            // assert
            val successes = results.mapNotNull { it.getOrNull() }
            val failures = results.mapNotNull { it.exceptionOrNull() }
            val usedCoupon = userCouponJpaRepository.findByIdAndDeletedAtIsNull(userCoupon.id!!)
            val remainingStock = stockJpaRepository.findByProductIdAndDeletedAtIsNull(product.id)

            assertAll(
                { assertThat(successes).hasSize(1) },
                { assertThat(successes.first().discountAmount).isEqualTo(1_000L) },
                { assertThat(successes.first().paymentAmount).isEqualTo(9_000L) },
                { assertThat(failures).hasSize(9) },
                { assertThat(failures).allSatisfy { assertThat(it).isInstanceOf(CoreException::class.java) } },
                {
                    assertThat(failures.map { (it as CoreException).errorType })
                        .allMatch { it == ErrorType.CONFLICT }
                },
                { assertThat(usedCoupon?.usedAt).isNotNull() },
                { assertThat(orderJpaRepository.findAll()).hasSize(1) },
                { assertThat(remainingStock?.quantity).isEqualTo(9) },
            )
        }

        @DisplayName("동일한 상품으로 재고보다 많은 주문이 동시에 요청되어도 재고 수량만큼만 성공한다.")
        @Test
        fun createsOrdersOnlyUpToStock_whenSameProductIsOrderedConcurrently() {
            // arrange
            val users = (1..10).map { index ->
                userJpaRepository.save(
                    newUserJpaEntity(
                        loginId = "user$index",
                        email = "user$index@example.com",
                    ),
                )
            }
            val product = saveProductWithStock(price = 10_000L, stock = 5)

            // act
            val results = runConcurrently(users.size) { index ->
                orderFacade.placeOrder(
                    CreateOrderCommand(
                        userId = users[index].id,
                        items = listOf(CreateOrderItemCommand(productId = product.id, quantity = 1)),
                    ),
                )
            }

            // assert
            val successes = results.mapNotNull { it.getOrNull() }
            val failures = results.mapNotNull { it.exceptionOrNull() }
            val remainingStock = stockJpaRepository.findByProductIdAndDeletedAtIsNull(product.id)

            assertAll(
                { assertThat(successes).hasSize(5) },
                { assertThat(successes).allSatisfy { assertThat(it.paymentAmount).isEqualTo(10_000L) } },
                { assertThat(failures).hasSize(5) },
                { assertThat(failures).allSatisfy { assertThat(it).isInstanceOf(CoreException::class.java) } },
                {
                    assertThat(failures.map { (it as CoreException).errorType })
                        .allMatch { it == ErrorType.BAD_REQUEST }
                },
                { assertThat(orderJpaRepository.findAll()).hasSize(5) },
                { assertThat(remainingStock?.quantity).isEqualTo(0) },
            )
        }
    }

    @DisplayName("주문 상태 전이 동시성 제어 시, ")
    @Nested
    inner class ChangeOrderStatusConcurrently {
        @DisplayName("동일 주문에 결제 성공과 실패 처리가 동시에 들어와도 하나의 상태 전이만 성공한다.")
        @Test
        fun changesOrderStatusOnlyOnce_whenConfirmAndReleaseAreRequestedConcurrently() {
            // arrange
            val user = userJpaRepository.save(newUserJpaEntity())
            val product = saveProductWithStock(price = 10_000L, stock = 10)
            val coupon = couponRepository.save(
                Coupon(name = "1000원 할인", policy = DiscountPolicy.FixedAmount(1_000L)),
            )
            val userCoupon = userCouponRepository.save(
                UserCoupon(userId = user.id, couponId = coupon.id!!),
            )
            val order = orderPrepareService.prepare(
                CreateOrderCommand(
                    userId = user.id,
                    items = listOf(CreateOrderItemCommand(productId = product.id, quantity = 1)),
                    userCouponId = userCoupon.id,
                ),
            )

            // act
            val results = runConcurrently(2) { index ->
                if (index == 0) {
                    orderConfirmService.confirm(order.id!!)
                } else {
                    orderReleaseService.markPaymentFailed(order.id!!)
                }
            }

            // assert
            val successes = results.mapNotNull { it.getOrNull() }
            val failures = results.mapNotNull { it.exceptionOrNull() }
            val savedOrder = orderJpaRepository.findWithItemsByIdAndDeletedAtIsNull(order.id!!)
            val userCouponEntity = userCouponJpaRepository.findByIdAndDeletedAtIsNull(userCoupon.id!!)
            val stock = stockJpaRepository.findByProductIdAndDeletedAtIsNull(product.id)

            assertAll(
                { assertThat(successes).hasSize(1) },
                { assertThat(failures).hasSize(1) },
                { assertThat(failures.single()).isInstanceOf(CoreException::class.java) },
                { assertThat((failures.single() as CoreException).errorType).isEqualTo(ErrorType.CONFLICT) },
                { assertThat(savedOrder?.status).isEqualTo(successes.single().status) },
                {
                    if (successes.single().status == OrderStatus.PAID) {
                        assertThat(userCouponEntity?.usedAt).isNotNull()
                        assertThat(stock?.quantity).isEqualTo(9)
                    } else {
                        assertThat(successes.single().status).isEqualTo(OrderStatus.PAYMENT_FAILED)
                        assertThat(userCouponEntity?.usedAt).isNull()
                        assertThat(stock?.quantity).isEqualTo(10)
                    }
                },
            )
        }
    }

    private fun <T> runConcurrently(
        times: Int,
        task: (Int) -> T,
    ): List<Result<T>> {
        val executor = Executors.newFixedThreadPool(times)
        val ready = CountDownLatch(times)
        val start = CountDownLatch(1)

        return try {
            val futures = (0 until times).map { index ->
                executor.submit(
                    Callable {
                        ready.countDown()
                        start.await()
                        runCatching { task(index) }
                    },
                )
            }
            ready.await()
            start.countDown()
            futures.map { it.get() }
        } finally {
            executor.shutdownNow()
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
                likeCount = 0,
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
