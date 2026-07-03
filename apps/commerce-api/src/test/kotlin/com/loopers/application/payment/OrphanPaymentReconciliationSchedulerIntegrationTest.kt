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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.client.ResourceAccessException
import java.time.LocalDate
import java.time.ZonedDateTime

@SpringBootTest
class OrphanPaymentReconciliationSchedulerIntegrationTest @Autowired constructor(
    private val scheduler: OrphanPaymentReconciliationScheduler,
    private val paymentFacade: PaymentFacade,
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

    @DisplayName("REQUESTED 상태의 Payment에 대해 PG에 SUCCESS 거래가 있으면 결제를 확정한다")
    @Test
    fun reconcile_confirmsPayment_whenPgHasSuccessTransaction() {
        // arrange
        val (order, payment) = placeOrderWithRequestedPayment()
        makePaymentOlderThan(payment.id!!, 10)

        fakePaymentGateway.orderTransactions[order.id.toString()] = listOf(
            PaymentTransactionInfo(
                transactionKey = "recovered:TR:001",
                orderId = order.id.toString(),
                cardType = "SAMSUNG",
                cardNo = "1234-5678-9012-3456",
                amount = 10_000L,
                status = PaymentStatus.SUCCESS,
                reason = "정상 승인",
            ),
        )

        // act
        scheduler.reconcile()

        // assert
        val updatedPayment = paymentRepository.findByOrderId(order.id)!!
        val updatedOrder = orderRepository.find(order.id)!!
        assertAll(
            { assertThat(updatedPayment.transactionKey).isEqualTo("recovered:TR:001") },
            { assertThat(updatedPayment.status).isEqualTo(PaymentStatus.SUCCESS) },
            { assertThat(updatedOrder.status).isEqualTo(OrderStatus.PAID) },
        )
    }

    @DisplayName("REQUESTED 상태의 Payment에 대해 PG에 거래가 없으면 FAILED 처리하고 주문을 해제한다")
    @Test
    fun reconcile_failsPayment_whenPgHasNoTransaction() {
        // arrange
        val (order, payment) = placeOrderWithRequestedPayment()
        makePaymentOlderThan(payment.id!!, 10)

        // act — PG에 거래 없음 (기본 empty)
        scheduler.reconcile()

        // assert
        val updatedPayment = paymentRepository.findByOrderId(order.id)!!
        val updatedOrder = orderRepository.find(order.id)!!
        assertAll(
            { assertThat(updatedPayment.status).isEqualTo(PaymentStatus.FAILED) },
            { assertThat(updatedOrder.status).isEqualTo(OrderStatus.PAYMENT_FAILED) },
        )
    }

    @DisplayName("REQUESTED 상태의 Payment에 대해 PG에 PENDING 거래가 있으면 PENDING으로 전환만 한다")
    @Test
    fun reconcile_marksPending_whenPgTransactionIsPending() {
        // arrange
        val (order, payment) = placeOrderWithRequestedPayment()
        makePaymentOlderThan(payment.id!!, 10)

        fakePaymentGateway.orderTransactions[order.id.toString()] = listOf(
            PaymentTransactionInfo(
                transactionKey = "recovered:TR:002",
                orderId = order.id.toString(),
                cardType = "SAMSUNG",
                cardNo = "1234-5678-9012-3456",
                amount = 10_000L,
                status = PaymentStatus.PENDING,
                reason = null,
            ),
        )

        // act
        scheduler.reconcile()

        // assert — PENDING으로 전환, 주문 상태는 그대로
        val updatedPayment = paymentRepository.findByOrderId(order.id)!!
        val updatedOrder = orderRepository.find(order.id)!!
        assertAll(
            { assertThat(updatedPayment.transactionKey).isEqualTo("recovered:TR:002") },
            { assertThat(updatedPayment.status).isEqualTo(PaymentStatus.PENDING) },
            { assertThat(updatedOrder.status).isEqualTo(OrderStatus.PENDING_PAYMENT) },
        )
    }

    @DisplayName("5분 미만인 REQUESTED Payment는 대사 대상에 포함되지 않는다")
    @Test
    fun reconcile_skipsRecentRequestedPayment() {
        // arrange
        val (order, _) = placeOrderWithRequestedPayment()

        fakePaymentGateway.orderTransactions[order.id.toString()] = listOf(
            PaymentTransactionInfo(
                transactionKey = "recovered:TR:003",
                orderId = order.id.toString(),
                cardType = "SAMSUNG",
                cardNo = "1234-5678-9012-3456",
                amount = 10_000L,
                status = PaymentStatus.SUCCESS,
                reason = "정상 승인",
            ),
        )

        // act — 방금 생성된 Payment이므로 5분 미만
        scheduler.reconcile()

        // assert — 여전히 REQUESTED
        val payment = paymentRepository.findByOrderId(order.id)!!
        assertThat(payment.status).isEqualTo(PaymentStatus.REQUESTED)
    }

    private fun placeOrderWithRequestedPayment(): Pair<com.loopers.application.order.OrderInfo, com.loopers.domain.payment.Payment> {
        val user = userJpaRepository.save(newUserJpaEntity())
        val product = saveProductWithStock(price = 10_000L, stock = 10)

        val order = orderFacade.placeOrder(
            CreateOrderCommand(
                userId = user.id,
                items = listOf(CreateOrderItemCommand(productId = product.id, quantity = 1)),
                userCouponId = null,
            ),
        )

        fakePaymentGateway.nextException = ResourceAccessException("Read timed out")
        try {
            paymentFacade.requestPayment(
                RequestPaymentCommand(
                    orderId = order.id,
                    userId = user.id,
                    cardType = "SAMSUNG",
                    cardNo = "1234-5678-9012-3456",
                ),
            )
        } catch (_: ResourceAccessException) {
        }
        fakePaymentGateway.nextException = null

        val payment = paymentRepository.findByOrderId(order.id)!!
        return order to payment
    }

    private fun makePaymentOlderThan(paymentId: Long, minutesAgo: Long) {
        transactionTemplate.execute {
            val pastTime = ZonedDateTime.now().minusMinutes(minutesAgo)
            entityManager.createNativeQuery(
                "UPDATE payments SET created_at = :createdAt WHERE id = :paymentId",
            )
                .setParameter("createdAt", pastTime)
                .setParameter("paymentId", paymentId)
                .executeUpdate()
            entityManager.flush()
            entityManager.clear()
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
            ),
        )
        stockJpaRepository.save(StockJpaEntity(productId = product.id, quantity = stock))
        return product
    }

    private var userSeq = 0

    private fun newUserJpaEntity() = UserJpaEntity(
        loginId = "recon${++userSeq}",
        encodedPassword = EncodedPassword("\$2a\$10\$existingHashedPassword."),
        name = "선데이",
        birthDate = LocalDate.of(1990, 1, 1),
        email = "seondays@example.com",
    )

    @TestConfiguration
    class ReconciliationTestConfiguration {
        @Bean
        @Primary
        fun fakePaymentGateway(): PaymentFacadeIntegrationTest.FakePaymentGateway {
            return PaymentFacadeIntegrationTest.FakePaymentGateway()
        }
    }
}
