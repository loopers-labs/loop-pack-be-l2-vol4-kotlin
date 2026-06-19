package com.loopers.application.order

import com.loopers.application.payment.PaymentCommand
import com.loopers.application.payment.PaymentCancelCommand
import com.loopers.application.payment.PaymentGateway
import com.loopers.application.payment.PaymentResult
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
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.LocalDate

@SpringBootTest
class OrderFacadeIntegrationTest @Autowired constructor(
    private val orderFacade: OrderFacade,
    private val orderReleaseService: OrderReleaseService,
    private val fakePaymentGateway: FakePaymentGateway,
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
        fakePaymentGateway.reset()
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("주문 결제 흐름 진행 시, ")
    @Nested
    inner class PlaceOrder {
        @DisplayName("결제에 성공하면 주문을 결제 완료 상태로 확정한다.")
        @Test
        fun placeOrder_marksOrderPaid_whenPaymentSucceeds() {
            // arrange
            val user = userJpaRepository.save(newUserJpaEntity())
            val product = saveProductWithStock(price = 10_000L, stock = 10)
            val coupon = couponRepository.save(
                Coupon(name = "1000원 할인", policy = DiscountPolicy.FixedAmount(1_000L)),
            )
            val userCoupon = userCouponRepository.save(
                UserCoupon(userId = user.id, couponId = coupon.id!!),
            )
            fakePaymentGateway.nextResult = PaymentResult.SUCCESS

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
                { assertThat(order.status).isEqualTo(OrderStatus.PAID) },
                { assertThat(order.paymentAmount).isEqualTo(9_000L) },
                { assertThat(fakePaymentGateway.lastCommand?.orderId).isEqualTo(order.id) },
                { assertThat(fakePaymentGateway.lastCommand?.userId).isEqualTo(user.id) },
                { assertThat(fakePaymentGateway.lastCommand?.amount?.amount).isEqualTo(9_000L) },
                { assertThat(fakePaymentGateway.cancelCommands).isEmpty() },
                { assertThat(usedCoupon?.usedAt).isNotNull() },
                { assertThat(remainingStock.quantity).isEqualTo(9) },
            )
        }

        @DisplayName("결제에 실패하면 주문을 실패 상태로 변경하고 차감된 재고와 쿠폰 사용을 복구한다.")
        @Test
        fun placeOrder_releasesOrder_whenPaymentFails() {
            // arrange
            val user = userJpaRepository.save(newUserJpaEntity())
            val product = saveProductWithStock(price = 10_000L, stock = 10)
            val coupon = couponRepository.save(
                Coupon(name = "1000원 할인", policy = DiscountPolicy.FixedAmount(1_000L)),
            )
            val userCoupon = userCouponRepository.save(
                UserCoupon(userId = user.id, couponId = coupon.id!!),
            )
            fakePaymentGateway.nextResult = PaymentResult.FAILED

            // act
            val order = orderFacade.placeOrder(
                CreateOrderCommand(
                    userId = user.id,
                    items = listOf(CreateOrderItemCommand(productId = product.id, quantity = 1)),
                    userCouponId = userCoupon.id,
                ),
            )

            // assert
            val restoredCoupon = userCouponJpaRepository.findByIdAndDeletedAtIsNull(userCoupon.id!!)
            val restoredStock = stockApplicationService.getStock(product.id)
            assertAll(
                { assertThat(order.status).isEqualTo(OrderStatus.PAYMENT_FAILED) },
                { assertThat(order.paymentAmount).isEqualTo(9_000L) },
                { assertThat(fakePaymentGateway.lastCommand?.orderId).isEqualTo(order.id) },
                { assertThat(fakePaymentGateway.lastCommand?.userId).isEqualTo(user.id) },
                { assertThat(fakePaymentGateway.lastCommand?.amount?.amount).isEqualTo(9_000L) },
                { assertThat(fakePaymentGateway.cancelCommands).isEmpty() },
                { assertThat(restoredCoupon?.usedAt).isNull() },
                { assertThat(restoredStock.quantity).isEqualTo(10) },
            )
        }

        @DisplayName("결제는 성공했지만 주문 확정에 실패하면 결제 취소를 요청한다.")
        @Test
        fun placeOrder_cancelsPayment_whenConfirmFailsAfterPaymentSuccess() {
            // arrange
            val user = userJpaRepository.save(newUserJpaEntity())
            val product = saveProductWithStock(price = 10_000L, stock = 10)
            val coupon = couponRepository.save(
                Coupon(name = "1000원 할인", policy = DiscountPolicy.FixedAmount(1_000L)),
            )
            val userCoupon = userCouponRepository.save(
                UserCoupon(userId = user.id, couponId = coupon.id!!),
            )
            fakePaymentGateway.nextResult = PaymentResult.SUCCESS
            fakePaymentGateway.beforePayReturns = { command ->
                orderReleaseService.markPaymentFailed(command.orderId)
            }

            // act
            val result = assertThrows<CoreException> {
                orderFacade.placeOrder(
                    CreateOrderCommand(
                        userId = user.id,
                        items = listOf(CreateOrderItemCommand(productId = product.id, quantity = 1)),
                        userCouponId = userCoupon.id,
                    ),
                )
            }

            // assert
            val restoredCoupon = userCouponJpaRepository.findByIdAndDeletedAtIsNull(userCoupon.id!!)
            val restoredStock = stockApplicationService.getStock(product.id)
            assertAll(
                { assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT) },
                { assertThat(fakePaymentGateway.cancelCommands).hasSize(1) },
                { assertThat(fakePaymentGateway.cancelCommands.single().orderId).isEqualTo(fakePaymentGateway.lastCommand?.orderId) },
                { assertThat(fakePaymentGateway.cancelCommands.single().userId).isEqualTo(user.id) },
                { assertThat(fakePaymentGateway.cancelCommands.single().amount.amount).isEqualTo(9_000L) },
                { assertThat(restoredCoupon?.usedAt).isNull() },
                { assertThat(restoredStock.quantity).isEqualTo(10) },
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

    @TestConfiguration
    class PaymentTestConfiguration {
        @Bean
        @Primary
        fun fakePaymentGateway(): FakePaymentGateway {
            return FakePaymentGateway()
        }
    }

    class FakePaymentGateway : PaymentGateway {
        var nextResult: PaymentResult = PaymentResult.SUCCESS
        var lastCommand: PaymentCommand? = null
        var beforePayReturns: ((PaymentCommand) -> Unit)? = null
        val cancelCommands: MutableList<PaymentCancelCommand> = mutableListOf()

        override fun pay(command: PaymentCommand): PaymentResult {
            lastCommand = command
            beforePayReturns?.invoke(command)
            return nextResult
        }

        override fun cancel(command: PaymentCancelCommand) {
            cancelCommands.add(command)
        }

        fun reset() {
            nextResult = PaymentResult.SUCCESS
            lastCommand = null
            beforePayReturns = null
            cancelCommands.clear()
        }
    }
}
