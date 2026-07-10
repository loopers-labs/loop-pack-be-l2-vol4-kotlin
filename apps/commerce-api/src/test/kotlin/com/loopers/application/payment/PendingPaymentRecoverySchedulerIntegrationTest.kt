package com.loopers.application.payment

import com.loopers.application.order.CreateOrderCommand
import com.loopers.application.order.CreateOrderItemCommand
import com.loopers.application.order.OrderFacade
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
import com.loopers.utils.DatabaseCleanUp
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.springframework.transaction.support.TransactionTemplate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.LocalDate
import java.time.ZonedDateTime

@SpringBootTest
class PendingPaymentRecoverySchedulerIntegrationTest @Autowired constructor(
    private val scheduler: PendingPaymentRecoveryScheduler,
    private val paymentFacade: PaymentFacade,
    private val paymentRequestProcessor: PaymentRequestProcessor,
    private val orderFacade: OrderFacade,
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
    private val fakePaymentGateway: PaymentFacadeIntegrationTest.FakePaymentGateway,
    private val productJpaRepository: ProductJpaRepository,
    private val stockJpaRepository: StockJpaRepository,
    private val userJpaRepository: UserJpaRepository,
    private val entityManager: EntityManager,
    private val transactionTemplate: TransactionTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        fakePaymentGateway.reset()
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("5분 이상 PENDING 상태인 결제를 PG에 조회하여 SUCCESS면 결제를 확정한다")
    @Test
    fun recover_confirmsPayment_whenPgStatusIsSuccess() {
        // arrange
        val (order, paymentInfo) = placeOrderAndRequestPayment()

        val transactionKey = paymentInfo.transactionKey!!
        makePaymentOlderThan(transactionKey, 10)

        fakePaymentGateway.transactionStatuses[transactionKey] = PaymentTransactionInfo(
            transactionKey = transactionKey,
            orderId = order.id.toString(),
            cardType = "SAMSUNG",
            cardNo = "1234-5678-9012-3456",
            amount = 10_000L,
            status = PaymentStatus.SUCCESS,
            reason = "정상 승인",
        )

        // act
        scheduler.recover()

        // assert
        val payment = paymentRepository.findByTransactionKey(transactionKey)!!
        val updatedOrder = orderRepository.find(order.id)!!
        assertAll(
            { assertThat(payment.status).isEqualTo(PaymentStatus.SUCCESS) },
            { assertThat(updatedOrder.status).isEqualTo(OrderStatus.PAID) },
        )
    }

    @DisplayName("5분 이상 PENDING 상태인 결제를 PG에 조회하여 FAILED면 결제를 실패 처리한다")
    @Test
    fun recover_failsPayment_whenPgStatusIsFailed() {
        // arrange
        val (order, paymentInfo) = placeOrderAndRequestPayment()

        val transactionKey = paymentInfo.transactionKey!!
        makePaymentOlderThan(transactionKey, 10)

        fakePaymentGateway.transactionStatuses[transactionKey] = PaymentTransactionInfo(
            transactionKey = transactionKey,
            orderId = order.id.toString(),
            cardType = "SAMSUNG",
            cardNo = "1234-5678-9012-3456",
            amount = 10_000L,
            status = PaymentStatus.FAILED,
            reason = "잔액 부족",
        )

        // act
        scheduler.recover()

        // assert
        val payment = paymentRepository.findByTransactionKey(transactionKey)!!
        val updatedOrder = orderRepository.find(order.id)!!
        assertAll(
            { assertThat(payment.status).isEqualTo(PaymentStatus.FAILED) },
            { assertThat(updatedOrder.status).isEqualTo(OrderStatus.PAYMENT_FAILED) },
        )
    }

    @DisplayName("5분 미만 PENDING 결제는 복구 대상에 포함되지 않는다")
    @Test
    fun recover_skipsRecentPendingPayment() {
        // arrange
        val (_, paymentInfo) = placeOrderAndRequestPayment()

        // act — createdAt이 방금이므로 5분 미만
        scheduler.recover()

        // assert — 여전히 PENDING
        val payment = paymentRepository.findByTransactionKey(paymentInfo.transactionKey!!)!!
        assertThat(payment.status).isEqualTo(PaymentStatus.PENDING)
    }

    @DisplayName("PG 조회 중 실패해도 다른 결제 건의 복구를 계속 진행한다")
    @Test
    fun recover_continuesOnFailure_whenOnePaymentFails() {
        // arrange — 2건의 PENDING 결제 생성
        val (order1, paymentInfo1) = placeOrderAndRequestPayment()
        val (order2, paymentInfo2) = placeOrderAndRequestPayment(productName = "Loopers Hoodie")

        val transactionKey1 = paymentInfo1.transactionKey!!
        val transactionKey2 = paymentInfo2.transactionKey!!
        makePaymentOlderThan(transactionKey1, 10)
        makePaymentOlderThan(transactionKey2, 10)

        // 1건은 PG 조회 실패, 2건은 SUCCESS
        fakePaymentGateway.transactionStatuses[transactionKey2] = PaymentTransactionInfo(
            transactionKey = transactionKey2,
            orderId = order2.id.toString(),
            cardType = "SAMSUNG",
            cardNo = "1234-5678-9012-3456",
            amount = 10_000L,
            status = PaymentStatus.SUCCESS,
            reason = "정상 승인",
        )

        // act
        scheduler.recover()

        // assert — 1건은 여전히 PENDING, 2건은 SUCCESS
        val payment1 = paymentRepository.findByTransactionKey(transactionKey1)!!
        val payment2 = paymentRepository.findByTransactionKey(transactionKey2)!!
        assertAll(
            { assertThat(payment1.status).isEqualTo(PaymentStatus.PENDING) },
            { assertThat(payment2.status).isEqualTo(PaymentStatus.SUCCESS) },
        )
    }

    private fun makePaymentOlderThan(transactionKey: String, minutesAgo: Long) {
        transactionTemplate.execute {
            val pastTime = ZonedDateTime.now().minusMinutes(minutesAgo)
            entityManager.createNativeQuery(
                "UPDATE payments SET created_at = :createdAt WHERE transaction_key = :transactionKey",
            )
                .setParameter("createdAt", pastTime)
                .setParameter("transactionKey", transactionKey)
                .executeUpdate()
            entityManager.flush()
            entityManager.clear()
        }
    }

    private fun placeOrderAndRequestPayment(
        productName: String = "Loopers T-Shirt",
    ): Pair<com.loopers.application.order.OrderInfo, PaymentInfo> {
        val user = userJpaRepository.save(newUserJpaEntity())
        val product = saveProductWithStock(name = productName, price = 10_000L, stock = 10)
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
        paymentRequestProcessor.process(
            paymentId = paymentInfo.id,
            callbackUrl = "http://localhost:8080/api/v1/payments/callback",
        )
        val processedPayment = paymentRepository.findByOrderId(order.id)!!

        return order to PaymentInfo.from(processedPayment)
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

    private var userSeq = 0

    private fun newUserJpaEntity() = UserJpaEntity(
        loginId = "test${++userSeq}",
        encodedPassword = EncodedPassword("\$2a\$10\$existingHashedPassword."),
        name = "선데이",
        birthDate = LocalDate.of(1990, 1, 1),
        email = "seondays@example.com",
    )

    @TestConfiguration
    class SchedulerTestConfiguration {
        @Bean
        @Primary
        fun fakePaymentGateway(): PaymentFacadeIntegrationTest.FakePaymentGateway {
            return PaymentFacadeIntegrationTest.FakePaymentGateway()
        }
    }
}
