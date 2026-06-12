package com.loopers.application.order

import com.loopers.application.coupon.CouponApplicationService
import com.loopers.domain.catalog.ProductStock
import com.loopers.domain.coupon.CouponCommand
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.IssuedCouponStatus
import com.loopers.domain.order.OrderCommand
import com.loopers.infrastructure.catalog.ProductStockJpaRepository
import com.loopers.infrastructure.coupon.IssuedCouponJpaRepository
import com.loopers.infrastructure.order.OrderJpaRepository
import com.loopers.infrastructure.order.StockReservationJpaRepository
import com.loopers.infrastructure.payment.PaymentJpaRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
class OrderCheckoutConcurrencyIntegrationTest @Autowired constructor(
    private val orderCheckoutFacade: OrderCheckoutFacade,
    private val couponApplicationService: CouponApplicationService,
    private val productStockJpaRepository: ProductStockJpaRepository,
    private val issuedCouponJpaRepository: IssuedCouponJpaRepository,
    private val orderJpaRepository: OrderJpaRepository,
    private val stockReservationJpaRepository: StockReservationJpaRepository,
    private val paymentJpaRepository: PaymentJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Test
    fun sameCouponCanOnlyBeUsedByOneConcurrentCheckout() {
        productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 10))
        val coupon = couponApplicationService.create(
            CouponCommand.Create(
                name = "동시성 쿠폰",
                type = CouponType.FIXED,
                value = 1000,
                minOrderAmount = null,
                expiredAt = LocalDateTime.now().plusDays(30),
            ),
        )
        couponApplicationService.issue(userId = 1L, couponId = coupon.couponId)
        val successes = AtomicInteger(0)

        runConcurrently(times = 8) {
            orderCheckoutFacade.checkout(checkoutCommand(userId = 1L, couponId = coupon.couponId))
            successes.incrementAndGet()
        }

        val issue = issuedCouponJpaRepository.findByUserIdAndCouponIdAndDeletedAtIsNull(1L, coupon.couponId)!!
        val stock = productStockJpaRepository.findByProductIdAndDeletedAtIsNull(10L)!!
        assertAll(
            { assertThat(successes.get()).isEqualTo(1) },
            { assertThat(issue.status).isEqualTo(IssuedCouponStatus.USED) },
            { assertThat(orderJpaRepository.count()).isEqualTo(1) },
            { assertThat(paymentJpaRepository.count()).isEqualTo(1) },
            { assertThat(stockReservationJpaRepository.count()).isEqualTo(1) },
            { assertThat(stock.reservedQuantity).isEqualTo(1) },
        )
    }

    @Test
    fun sameProductStockIsReservedOnlyUpToAvailableQuantityUnderConcurrentCheckout() {
        productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 3))
        val successes = AtomicInteger(0)

        runConcurrently(times = 8) { index ->
            orderCheckoutFacade.checkout(checkoutCommand(userId = index + 1L, couponId = null))
            successes.incrementAndGet()
        }

        val stock = productStockJpaRepository.findByProductIdAndDeletedAtIsNull(10L)!!
        assertAll(
            { assertThat(successes.get()).isEqualTo(3) },
            { assertThat(orderJpaRepository.count()).isEqualTo(3) },
            { assertThat(paymentJpaRepository.count()).isEqualTo(3) },
            { assertThat(stockReservationJpaRepository.count()).isEqualTo(3) },
            { assertThat(stock.stockQuantity).isEqualTo(3) },
            { assertThat(stock.reservedQuantity).isEqualTo(3) },
        )
    }

    private fun checkoutCommand(userId: Long, couponId: Long?): OrderCommand.Checkout =
        OrderCommand.Checkout(
            userId = userId,
            items = listOf(OrderCommand.CheckoutItem(10L, "상품A", "브랜드A", 1000L, 1)),
            deliveryAddress = "서울시 강남구",
            deliveryRequest = "문 앞",
            phoneNumber = "010-1234-5678",
            reservationExpiresAt = LocalDateTime.now().plusMinutes(10),
            couponId = couponId,
        )

    private fun runConcurrently(times: Int, block: (Long) -> Unit) {
        val executor = Executors.newFixedThreadPool(times)
        val ready = CountDownLatch(times)
        val start = CountDownLatch(1)
        val done = CountDownLatch(times)

        repeat(times) { index ->
            executor.submit {
                try {
                    ready.countDown()
                    start.await(3, TimeUnit.SECONDS)
                    block(index.toLong())
                } catch (_: Throwable) {
                } finally {
                    done.countDown()
                }
            }
        }

        ready.await(3, TimeUnit.SECONDS)
        start.countDown()
        done.await(10, TimeUnit.SECONDS)
        executor.shutdown()
    }
}
