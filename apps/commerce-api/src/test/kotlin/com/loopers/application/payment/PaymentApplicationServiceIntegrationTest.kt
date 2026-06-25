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
import com.loopers.domain.stock.StockService
import com.loopers.interfaces.api.order.OrderApplicationServicePort
import com.loopers.interfaces.api.payment.PaymentApplicationServicePort
import com.loopers.interfaces.api.product.ProductAdminApplicationServicePort
import com.loopers.interfaces.api.user.UserApplicationServicePort
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.test.FakePaymentGatewayConfig
import com.loopers.test.FakePgPaymentGateway
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
    private val stockService: StockService,
    private val fakePgPaymentGateway: FakePgPaymentGateway,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        fakePgPaymentGateway.reset()
    }

    data class OrderSetup(val userId: Long, val orderId: Long, val amount: Long, val productId: Long)

    /** 사용자 + 상품(재고 5) + CREATED 주문(1개)을 세팅한다. */
    private fun setupOrder(loginId: String = "buyer01"): OrderSetup {
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
        return OrderSetup(userId, order.id, order.totalAmount, productId)
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

        @DisplayName("FAILED 콜백을 처리하면 결제가 실패하고 주문이 취소되며 재고가 복원된다.")
        @Test
        fun failsAndCompensates_onFailed() {
            val setup = setupOrder()
            val transactionKey = pay(setup.userId, setup.orderId).transactionKey
            // 주문 생성 시 재고 5 → 1 차감되어 4
            assertThat(stockService.getByProductId(setup.productId).quantity).isEqualTo(4)

            paymentApplicationService.handleCallback(
                PaymentCallbackCommand(transactionKey = transactionKey, status = PaymentStatus.FAILED, reason = "한도초과"),
            )

            assertThat(paymentRepositoryPort.findByTransactionKey(transactionKey)?.status).isEqualTo(PaymentStatus.FAILED)
            assertThat(orderRepositoryPort.findById(setup.orderId)?.status).isEqualTo(OrderStatus.CANCELLED)
            // 재고 원복: 4 → 5
            assertThat(stockService.getByProductId(setup.productId).quantity).isEqualTo(5)
        }

        @DisplayName("PENDING 콜백은 무시되어 결제와 주문 상태가 유지된다.")
        @Test
        fun ignored_onPending() {
            val (userId, orderId, _) = setupOrder()
            val transactionKey = pay(userId, orderId).transactionKey

            paymentApplicationService.handleCallback(
                PaymentCallbackCommand(transactionKey = transactionKey, status = PaymentStatus.PENDING, reason = null),
            )

            assertThat(paymentRepositoryPort.findByTransactionKey(transactionKey)?.status).isEqualTo(PaymentStatus.PENDING)
            assertThat(orderRepositoryPort.findById(orderId)?.status).isEqualTo(OrderStatus.PAYMENT_PENDING)
        }

        @DisplayName("이미 처리된 결제의 중복 SUCCESS 콜백은 멱등하게 무시된다.")
        @Test
        fun idempotent_onDuplicateSuccess() {
            val (userId, orderId, _) = setupOrder()
            val transactionKey = pay(userId, orderId).transactionKey
            val callback = PaymentCallbackCommand(transactionKey = transactionKey, status = PaymentStatus.SUCCESS, reason = "ok")
            paymentApplicationService.handleCallback(callback)

            // 두 번째 콜백은 예외 없이 무시되어야 한다.
            paymentApplicationService.handleCallback(callback)

            assertThat(paymentRepositoryPort.findByTransactionKey(transactionKey)?.status).isEqualTo(PaymentStatus.SUCCESS)
            assertThat(orderRepositoryPort.findById(orderId)?.status).isEqualTo(OrderStatus.PAYMENT_COMPLETED)
        }
    }

    @DisplayName("reconcile")
    @Nested
    inner class Reconcile {
        @DisplayName("PG 조회 결과가 SUCCESS 면 결제가 승인되고 주문이 결제완료로 복구된다.")
        @Test
        fun completes_whenPgSuccess() {
            val (userId, orderId, _) = setupOrder()
            val transactionKey = pay(userId, orderId).transactionKey
            fakePgPaymentGateway.stubTransaction(transactionKey, PaymentStatus.SUCCESS, "정상 승인")

            val result = paymentApplicationService.reconcile(transactionKey)

            assertThat(result.status).isEqualTo(PaymentStatus.SUCCESS)
            assertThat(paymentRepositoryPort.findByTransactionKey(transactionKey)?.status).isEqualTo(PaymentStatus.SUCCESS)
            assertThat(orderRepositoryPort.findById(orderId)?.status).isEqualTo(OrderStatus.PAYMENT_COMPLETED)
        }

        @DisplayName("PG 조회 결과가 FAILED 면 결제가 실패하고 주문 취소·재고 복원으로 복구된다.")
        @Test
        fun failsAndCompensates_whenPgFailed() {
            val setup = setupOrder()
            val transactionKey = pay(setup.userId, setup.orderId).transactionKey
            fakePgPaymentGateway.stubTransaction(transactionKey, PaymentStatus.FAILED, "한도초과")
            assertThat(stockService.getByProductId(setup.productId).quantity).isEqualTo(4)

            val result = paymentApplicationService.reconcile(transactionKey)

            assertThat(result.status).isEqualTo(PaymentStatus.FAILED)
            assertThat(orderRepositoryPort.findById(setup.orderId)?.status).isEqualTo(OrderStatus.CANCELLED)
            assertThat(stockService.getByProductId(setup.productId).quantity).isEqualTo(5)
        }

        @DisplayName("PG 에 거래가 아직 없으면(미확정) 상태를 유지한다.")
        @Test
        fun keepsPending_whenPgHasNoTransaction() {
            val (userId, orderId, _) = setupOrder()
            val transactionKey = pay(userId, orderId).transactionKey
            // stub 하지 않음 → PG 조회 null

            val result = paymentApplicationService.reconcile(transactionKey)

            assertThat(result.status).isEqualTo(PaymentStatus.PENDING)
            assertThat(orderRepositoryPort.findById(orderId)?.status).isEqualTo(OrderStatus.PAYMENT_PENDING)
        }

        @DisplayName("이미 종료된 결제건은 PG 조회 없이 멱등하게 현재 상태를 반환한다.")
        @Test
        fun idempotent_whenAlreadyResolved() {
            val (userId, orderId, _) = setupOrder()
            val transactionKey = pay(userId, orderId).transactionKey
            paymentApplicationService.handleCallback(
                PaymentCallbackCommand(transactionKey = transactionKey, status = PaymentStatus.SUCCESS, reason = "ok"),
            )
            // 종료 후 PG 가 다른 값을 줘도 무시되어야 한다.
            fakePgPaymentGateway.stubTransaction(transactionKey, PaymentStatus.FAILED, "늦은 실패")

            val result = paymentApplicationService.reconcile(transactionKey)

            assertThat(result.status).isEqualTo(PaymentStatus.SUCCESS)
            assertThat(orderRepositoryPort.findById(orderId)?.status).isEqualTo(OrderStatus.PAYMENT_COMPLETED)
        }
    }
}
