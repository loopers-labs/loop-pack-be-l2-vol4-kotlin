package com.loopers.application.payment

import com.loopers.application.order.CreateOrderCommand
import com.loopers.application.order.CreateOrderItemCommand
import com.loopers.application.order.OrderFacade
import com.loopers.application.order.OrderInfo
import com.loopers.application.stock.StockApplicationService
import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.DiscountPolicy
import com.loopers.domain.coupon.UserCoupon
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.user.EncodedPassword
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
class PaymentFacadeIntegrationTest @Autowired constructor(
    private val paymentFacade: PaymentFacade,
    private val orderFacade: OrderFacade,
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
    private val fakePaymentGateway: FakePaymentGateway,
    private val stockApplicationService: StockApplicationService,
    private val productJpaRepository: ProductJpaRepository,
    private val stockJpaRepository: StockJpaRepository,
    private val userJpaRepository: UserJpaRepository,
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        fakePaymentGateway.reset()
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("결제 요청 시, ")
    @Nested
    inner class RequestPayment {
        @DisplayName("PENDING_PAYMENT 상태의 주문에 대해 PG에 결제를 요청하고 Payment를 저장한다.")
        @Test
        fun requestPayment_savesPayment_whenOrderIsPendingPayment() {
            // arrange
            val user = userJpaRepository.save(newUserJpaEntity())
            val product = saveProductWithStock(price = 10_000L, stock = 10)
            fakePaymentGateway.nextStatus = PaymentStatus.PENDING

            val order = orderFacade.placeOrder(
                CreateOrderCommand(
                    userId = user.id,
                    items = listOf(CreateOrderItemCommand(productId = product.id, quantity = 1)),
                    userCouponId = null,
                ),
            )

            // act
            val result = paymentFacade.requestPayment(
                RequestPaymentCommand(
                    orderId = order.id,
                    userId = user.id,
                    cardType = "SAMSUNG",
                    cardNo = "1234-5678-9012-3456",
                ),
            )

            // assert
            val savedPayment = paymentRepository.findByOrderId(order.id)!!
            assertAll(
                { assertThat(result.status).isEqualTo(PaymentStatus.PENDING) },
                { assertThat(result.transactionKey).isNotBlank() },
                { assertThat(savedPayment.orderId).isEqualTo(order.id) },
                { assertThat(savedPayment.cardType).isEqualTo("SAMSUNG") },
                { assertThat(savedPayment.amount).isEqualTo(10_000L) },
                { assertThat(savedPayment.status).isEqualTo(PaymentStatus.PENDING) },
            )
        }

        @DisplayName("존재하지 않는 주문에 대해 결제를 요청하면 NOT_FOUND 예외가 발생한다.")
        @Test
        fun requestPayment_throwsNotFound_whenOrderDoesNotExist() {
            // arrange
            val user = userJpaRepository.save(newUserJpaEntity())

            // act & assert
            val result = assertThrows<CoreException> {
                paymentFacade.requestPayment(
                    RequestPaymentCommand(
                        orderId = 999L,
                        userId = user.id,
                        cardType = "SAMSUNG",
                        cardNo = "1234-5678-9012-3456",
                    ),
                )
            }
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("콜백 수신 시, ")
    @Nested
    inner class HandleCallback {
        @DisplayName("SUCCESS 콜백이 오면 Payment를 SUCCESS로, Order를 PAID로 변경한다.")
        @Test
        fun handleCallback_marksSuccess_whenCallbackIsSuccess() {
            // arrange
            val (order, paymentInfo) = placeOrderAndRequestPayment()

            // act
            paymentFacade.handleCallback(
                PaymentCallbackCommand(
                    transactionKey = paymentInfo.transactionKey,
                    status = PaymentStatus.SUCCESS,
                    reason = "정상 승인되었습니다.",
                ),
            )

            // assert
            val payment = paymentRepository.findByTransactionKey(paymentInfo.transactionKey)!!
            val updatedOrder = orderRepository.find(order.id)!!
            assertAll(
                { assertThat(payment.status).isEqualTo(PaymentStatus.SUCCESS) },
                { assertThat(payment.reason).isEqualTo("정상 승인되었습니다.") },
                { assertThat(updatedOrder.status).isEqualTo(OrderStatus.PAID) },
            )
        }

        @DisplayName("FAILED 콜백이 오면 Payment를 FAILED로, Order를 PAYMENT_FAILED로 변경하고 재고와 쿠폰을 복구한다.")
        @Test
        fun handleCallback_marksFailed_andReleasesResources() {
            // arrange
            val (order, paymentInfo) = placeOrderAndRequestPaymentWithCoupon()

            // act
            paymentFacade.handleCallback(
                PaymentCallbackCommand(
                    transactionKey = paymentInfo.transactionKey,
                    status = PaymentStatus.FAILED,
                    reason = "잔액 부족",
                ),
            )

            // assert
            val payment = paymentRepository.findByTransactionKey(paymentInfo.transactionKey)!!
            val updatedOrder = orderRepository.find(order.id)!!
            val remainingStock = stockApplicationService.getStock(1L)
            assertAll(
                { assertThat(payment.status).isEqualTo(PaymentStatus.FAILED) },
                { assertThat(payment.reason).isEqualTo("잔액 부족") },
                { assertThat(updatedOrder.status).isEqualTo(OrderStatus.PAYMENT_FAILED) },
                { assertThat(remainingStock.quantity).isEqualTo(10) },
            )
        }

        @DisplayName("이미 처리된 Payment에 대해 중복 콜백이 오면 CONFLICT 예외가 발생한다.")
        @Test
        fun handleCallback_throwsConflict_whenAlreadyProcessed() {
            // arrange
            val (_, paymentInfo) = placeOrderAndRequestPayment()
            paymentFacade.handleCallback(
                PaymentCallbackCommand(
                    transactionKey = paymentInfo.transactionKey,
                    status = PaymentStatus.SUCCESS,
                    reason = "정상 승인되었습니다.",
                ),
            )

            // act & assert
            val result = assertThrows<CoreException> {
                paymentFacade.handleCallback(
                    PaymentCallbackCommand(
                        transactionKey = paymentInfo.transactionKey,
                        status = PaymentStatus.SUCCESS,
                        reason = "정상 승인되었습니다.",
                    ),
                )
            }
            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
        }
    }

    private fun placeOrderAndRequestPayment(): Pair<OrderInfo, PaymentInfo> {
        val user = userJpaRepository.save(newUserJpaEntity())
        val product = saveProductWithStock(price = 10_000L, stock = 10)
        fakePaymentGateway.nextStatus = PaymentStatus.PENDING

        val order = orderFacade.placeOrder(
            CreateOrderCommand(
                userId = user.id,
                items = listOf(CreateOrderItemCommand(productId = product.id, quantity = 1)),
                userCouponId = null,
            ),
        )

        val paymentInfo = paymentFacade.requestPayment(
            RequestPaymentCommand(
                orderId = order.id,
                userId = user.id,
                cardType = "SAMSUNG",
                cardNo = "1234-5678-9012-3456",
            ),
        )

        return order to paymentInfo
    }

    private fun placeOrderAndRequestPaymentWithCoupon(): Pair<OrderInfo, PaymentInfo> {
        val user = userJpaRepository.save(newUserJpaEntity())
        val product = saveProductWithStock(price = 10_000L, stock = 10)
        val coupon = couponRepository.save(
            Coupon(name = "1000원 할인", policy = DiscountPolicy.FixedAmount(1_000L)),
        )
        val userCoupon = userCouponRepository.save(
            UserCoupon(userId = user.id, couponId = coupon.id!!),
        )
        fakePaymentGateway.nextStatus = PaymentStatus.PENDING

        val order = orderFacade.placeOrder(
            CreateOrderCommand(
                userId = user.id,
                items = listOf(CreateOrderItemCommand(productId = product.id, quantity = 1)),
                userCouponId = userCoupon.id,
            ),
        )

        val paymentInfo = paymentFacade.requestPayment(
            RequestPaymentCommand(
                orderId = order.id,
                userId = user.id,
                cardType = "SAMSUNG",
                cardNo = "1234-5678-9012-3456",
            ),
        )

        return order to paymentInfo
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

    @TestConfiguration
    class PaymentTestConfiguration {
        @Bean
        @Primary
        fun fakePaymentGateway(): FakePaymentGateway {
            return FakePaymentGateway()
        }
    }

    class FakePaymentGateway : PaymentGateway {
        var nextStatus: PaymentStatus = PaymentStatus.PENDING
        var lastCommand: PaymentCommand? = null
        val cancelCommands: MutableList<PaymentCancelCommand> = mutableListOf()

        override fun pay(command: PaymentCommand): PaymentResult {
            lastCommand = command
            return PaymentResult(
                transactionKey = "fake:TR:${System.currentTimeMillis()}",
                status = nextStatus,
                reason = null,
            )
        }

        override fun cancel(command: PaymentCancelCommand) {
            cancelCommands.add(command)
        }

        override fun getTransactionStatus(transactionKey: String): PaymentTransactionInfo {
            throw UnsupportedOperationException()
        }

        fun reset() {
            nextStatus = PaymentStatus.PENDING
            lastCommand = null
            cancelCommands.clear()
        }
    }
}
