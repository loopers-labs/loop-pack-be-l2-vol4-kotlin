package com.loopers.application.payment

import com.loopers.application.order.CreateOrderCommand
import com.loopers.application.order.CreateOrderItemCommand
import com.loopers.application.product.CreateProductCommand
import com.loopers.application.user.SignupCommand
import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandRepositoryPort
import com.loopers.domain.order.OrderRepositoryPort
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.PaymentRepositoryPort
import com.loopers.domain.payment.PaymentStatus
import com.loopers.interfaces.api.order.OrderApplicationServicePort
import com.loopers.interfaces.api.payment.PaymentApplicationServicePort
import com.loopers.interfaces.api.product.ProductAdminApplicationServicePort
import com.loopers.interfaces.api.user.UserApplicationServicePort
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.test.FakePaymentGatewayConfig
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.time.LocalDate

@SpringBootTest
@Import(FakePaymentGatewayConfig::class)
class PaymentApplicationServiceIntegrationTest @Autowired constructor(
    private val paymentApplicationService: PaymentApplicationServicePort,
    private val orderApplicationService: OrderApplicationServicePort,
    private val userApplicationService: UserApplicationServicePort,
    private val productAdminApplicationService: ProductAdminApplicationServicePort,
    private val brandRepositoryPort: BrandRepositoryPort,
    private val orderRepositoryPort: OrderRepositoryPort,
    private val paymentRepositoryPort: PaymentRepositoryPort,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    /** 사용자 + 상품 + CREATED 주문을 세팅하고 (userId, orderId, amount) 를 반환한다. */
    private fun setupOrder(loginId: String = "buyer01"): Triple<Long, Long, Long> {
        val userId = userApplicationService.signup(
            SignupCommand(
                loginId = loginId,
                rawPassword = "password1234",
                name = "테스터",
                birth = LocalDate.of(2000, 1, 1),
                email = "$loginId@example.com",
            ),
        ).id
        val brand = brandRepositoryPort.save(Brand.create(name = "Nike-${System.nanoTime()}", description = "x"))
        val productId = productAdminApplicationService.createProduct(
            CreateProductCommand(name = "p", price = 5_000L, description = "d", brandId = brand.id, quantity = 5),
        ).id
        val order = orderApplicationService.createOrder(
            CreateOrderCommand(userId = userId, items = listOf(CreateOrderItemCommand(productId, 1))),
        )
        return Triple(userId, order.id, order.totalAmount)
    }

    private fun pay(userId: Long, orderId: Long): PaymentResult =
        paymentApplicationService.pay(
            PayCommand(userId = userId, orderId = orderId, cardType = "SAMSUNG", cardNo = "1234-5678-9814-1451"),
        )

    @DisplayName("pay")
    @Nested
    inner class Pay {
        @DisplayName("결제를 요청하면 PENDING 결제건이 저장되고 주문이 결제대기로 전이된다.")
        @Test
        fun savesPendingPayment_andTransitionsOrder() {
            val (userId, orderId, amount) = setupOrder()

            val result = pay(userId, orderId)

            val payment = paymentRepositoryPort.findByTransactionKey(result.transactionKey)
            assertThat(payment?.status).isEqualTo(PaymentStatus.PENDING)
            assertThat(payment?.amount).isEqualTo(amount)
            assertThat(payment?.orderId).isEqualTo(orderId)
            assertThat(orderRepositoryPort.findById(orderId)?.status).isEqualTo(OrderStatus.PAYMENT_PENDING)
        }

        @DisplayName("존재하지 않는 주문으로 결제하면 NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenOrderMissing() {
            val (userId, _, _) = setupOrder()

            val ex = assertThrows<CoreException> { pay(userId, orderId = 999_999L) }
            assertThat(ex.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("handleCallback")
    @Nested
    inner class HandleCallback {
        @DisplayName("SUCCESS 콜백을 처리하면 결제가 승인되고 주문이 결제완료로 전이된다.")
        @Test
        fun approvesAndCompletes_onSuccess() {
            val (userId, orderId, _) = setupOrder()
            val transactionKey = pay(userId, orderId).transactionKey

            paymentApplicationService.handleCallback(
                PaymentCallbackCommand(transactionKey = transactionKey, status = PaymentStatus.SUCCESS, reason = "ok"),
            )

            assertThat(paymentRepositoryPort.findByTransactionKey(transactionKey)?.status).isEqualTo(PaymentStatus.SUCCESS)
            assertThat(orderRepositoryPort.findById(orderId)?.status).isEqualTo(OrderStatus.PAYMENT_COMPLETED)
        }

        @DisplayName("FAILED 콜백은 무시되어 결제와 주문 상태가 유지된다.")
        @Test
        fun ignored_onFailed() {
            val (userId, orderId, _) = setupOrder()
            val transactionKey = pay(userId, orderId).transactionKey

            paymentApplicationService.handleCallback(
                PaymentCallbackCommand(transactionKey = transactionKey, status = PaymentStatus.FAILED, reason = "한도초과"),
            )

            assertThat(paymentRepositoryPort.findByTransactionKey(transactionKey)?.status).isEqualTo(PaymentStatus.PENDING)
            assertThat(orderRepositoryPort.findById(orderId)?.status).isEqualTo(OrderStatus.PAYMENT_PENDING)
        }
    }
}
