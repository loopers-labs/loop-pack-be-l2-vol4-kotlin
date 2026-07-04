package com.loopers.infrastructure.payment

import com.loopers.application.payment.PaymentFacade
import com.loopers.application.payment.PaymentReconciler
import com.loopers.domain.BaseEntity
import com.loopers.domain.order.InMemoryOrderRepository
import com.loopers.domain.order.OrderItemModel
import com.loopers.domain.order.OrderModel
import com.loopers.domain.order.OrderService
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.InMemoryPaymentRepository
import com.loopers.domain.payment.PaymentModel
import com.loopers.domain.payment.PaymentResult
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.stock.InMemoryStockRepository
import com.loopers.domain.stock.StockModel
import com.loopers.domain.stock.StockService
import com.loopers.support.error.FakePaymentPort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.Duration
import java.time.ZonedDateTime

class PaymentReconcilerTest {
    private val inMemoryPaymentRepository = InMemoryPaymentRepository()
    private val inMemoryOrderRepository = InMemoryOrderRepository()
    private val inMemoryStockRepository = InMemoryStockRepository()
    private val fakePaymentPort = FakePaymentPort()

    private val paymentService = PaymentService(inMemoryPaymentRepository)
    private val orderService = OrderService(inMemoryOrderRepository)
    private val stockService = StockService(inMemoryStockRepository)
    private val paymentFacade = PaymentFacade(paymentService, orderService, stockService, fakePaymentPort, ApplicationEventPublisher {})

    private val staleThreshold = Duration.ofSeconds(30)
    private val reconciler = PaymentReconciler(paymentService, paymentFacade, staleThreshold, fakePaymentPort)

    private data class Pending(val orderId: Long, val productId: Long, val txKey: String)

    private fun givenStalePending(productId: Long, quantity: Int = 2, stockQty: Int = 8): Pending {
        inMemoryStockRepository.save(StockModel.of(productId, stockQty))
        val order = inMemoryOrderRepository.save(
            OrderModel.of(1L, listOf(OrderItemModel.of(productId, "상품", 1000.0, quantity))),
        )
        paymentFacade.requestPayment("user1", order.id, CardType.SAMSUNG, "1234-1234-1234-1234")
        val payment = paymentService.findByOrderId(order.id)!!.assignCreatedAt(ZonedDateTime.now().minusMinutes(1))
        return Pending(order.id, productId, payment.transactionKey!!)
    }

    private fun pgHasResolved(txKey: String, status: PaymentStatus, reason: String? = null) {
        fakePaymentPort.transactions[txKey] = PaymentResult(0L, txKey, status, reason)
    }

    private fun PaymentModel.assignCreatedAt(time: ZonedDateTime) = apply {
        BaseEntity::class.java.getDeclaredField("createdAt").also {
            it.isAccessible = true
            it.set(this, time)
        }
    }

    @Nested
    @DisplayName("콜백이 유실되어 PENDING 에 멈춘 결제를")
    inner class CallbackLost {
        @DisplayName("PG 에 SUCCESS 로 확정돼 있으면 결제 SUCCESS, 주문 CONFIRMED 로 수렴시킨다.")
        @Test
        fun convergesToSuccess() {
            // given
            val pending = givenStalePending(productId = 1L)
            pgHasResolved(pending.txKey, PaymentStatus.SUCCESS)

            // when
            reconciler.reconcile()

            // then
            assertThat(paymentService.findByOrderId(pending.orderId)!!.status).isEqualTo(PaymentStatus.SUCCESS)
            assertThat(inMemoryOrderRepository.findByIdOrNull(pending.orderId)!!.status).isEqualTo(OrderStatus.CONFIRMED)
        }

        @DisplayName("PG 에 FAILED 로 확정돼 있으면 결제 FAILED, 주문 CANCELLED, 재고를 복원한다.")
        @Test
        fun convergesToFailureAndRestoresStock() {
            // given
            val pending = givenStalePending(productId = 2L, quantity = 2, stockQty = 8)
            pgHasResolved(pending.txKey, PaymentStatus.FAILED, "한도초과")

            // when
            reconciler.reconcile()

            // then
            assertThat(paymentService.findByOrderId(pending.orderId)!!.status).isEqualTo(PaymentStatus.FAILED)
            assertThat(inMemoryOrderRepository.findByIdOrNull(pending.orderId)!!.status).isEqualTo(OrderStatus.CANCELLED)
            assertThat(inMemoryStockRepository.findStockByProductId(2L)!!.quantity).isEqualTo(10)
        }

        @DisplayName("PG 도 아직 PENDING 이면 아무 전이도 하지 않고 다음 주기로 미룬다.")
        @Test
        fun staysPendingWhenPgStillPending() {
            // given
            val pending = givenStalePending(productId = 3L)
            pgHasResolved(pending.txKey, PaymentStatus.PENDING)

            // when
            reconciler.reconcile()

            // then
            assertThat(paymentService.findByOrderId(pending.orderId)!!.status).isEqualTo(PaymentStatus.PENDING)
            assertThat(inMemoryOrderRepository.findByIdOrNull(pending.orderId)!!.status).isEqualTo(OrderStatus.PENDING)
        }
    }

    @Nested
    @DisplayName("stale 판정과 견고성에서")
    inner class Robustness {
        @DisplayName("생성된 지 얼마 안 된(임계 이내) PENDING 은 아직 reconcile 대상이 아니다.")
        @Test
        fun freshPendingIsNotTouched() {
            // given: createdAt 을 '지금'으로 두면 threshold(now-30s) 이후라 조회되지 않는다
            val pending = givenStalePending(productId = 4L)
            paymentService.findByOrderId(pending.orderId)!!.assignCreatedAt(ZonedDateTime.now())
            pgHasResolved(pending.txKey, PaymentStatus.SUCCESS)

            // when
            reconciler.reconcile()

            // then
            assertThat(paymentService.findByOrderId(pending.orderId)!!.status).isEqualTo(PaymentStatus.PENDING)
        }

        @DisplayName("한 건이 실패해도(PG 가 거래를 모름) 나머지 건은 정상 수렴시킨다.")
        @Test
        fun oneFailureDoesNotStopBatch() {
            // given: broken 은 transactions 미등록이라 getTransaction 이 예외, healthy 는 SUCCESS
            val broken = givenStalePending(productId = 5L)
            val healthy = givenStalePending(productId = 6L)
            pgHasResolved(healthy.txKey, PaymentStatus.SUCCESS)

            // when
            reconciler.reconcile()

            // then
            assertThat(paymentService.findByOrderId(broken.orderId)!!.status).isEqualTo(PaymentStatus.PENDING)
            assertThat(paymentService.findByOrderId(healthy.orderId)!!.status).isEqualTo(PaymentStatus.SUCCESS)
        }
    }

    @Nested
    @DisplayName("PG 미접수(txKey 없음) 건은")
    inner class NoTransactionKey {
        @DisplayName("autoFail 로 결제 FAILED, 주문 CANCELLED, 재고를 복원한다.")
        @Test
        fun autoFailsWhenNoTransactionKey() {
            // given: PG 호출 자체가 실패하여 txKey=null 인 PENDING
            fakePaymentPort.pay = { throw RuntimeException("PG 500") }
            inMemoryStockRepository.save(StockModel.of(7L, 8))
            val order = inMemoryOrderRepository.save(
                OrderModel.of(1L, listOf(OrderItemModel.of(7L, "상품", 1000.0, 2))),
            )
            paymentFacade.requestPayment("user1", order.id, CardType.SAMSUNG, "1234-1234-1234-1234")
            paymentService.findByOrderId(order.id)!!.assignCreatedAt(ZonedDateTime.now().minusMinutes(1))

            // when
            reconciler.reconcile()

            // then
            assertThat(paymentService.findByOrderId(order.id)!!.status).isEqualTo(PaymentStatus.FAILED)
            assertThat(inMemoryOrderRepository.findByIdOrNull(order.id)!!.status).isEqualTo(OrderStatus.CANCELLED)
            assertThat(inMemoryStockRepository.findStockByProductId(7L)!!.quantity).isEqualTo(10)
        }
    }

    @Nested
    @DisplayName("거짓음성(타임아웃으로 txKey 분실)이지만 PG 엔 거래가 있는 건은")
    inner class FalseNegativeRecovery {
        private fun givenStalePendingWithoutTxKey(productId: Long, quantity: Int = 2, stockQty: Int = 8): Long {
            fakePaymentPort.pay = { throw RuntimeException("PG timeout") }
            inMemoryStockRepository.save(StockModel.of(productId, stockQty))
            val order = inMemoryOrderRepository.save(
                OrderModel.of(1L, listOf(OrderItemModel.of(productId, "상품", 1000.0, quantity))),
            )
            paymentFacade.requestPayment("user1", order.id, CardType.SAMSUNG, "1234-1234-1234-1234")
            paymentService.findByOrderId(order.id)!!.assignCreatedAt(ZonedDateTime.now().minusMinutes(1))
            return order.id
        }

        @DisplayName("PG 역조회 1건 SUCCESS 면 txKey 를 회수해 결제 SUCCESS·주문 CONFIRMED 로 복구한다.")
        @Test
        fun recoversToSuccess() {
            // given
            val orderId = givenStalePendingWithoutTxKey(productId = 8L)
            fakePaymentPort.findByOrderIdResult =
                listOf(PaymentResult(orderId, "TR-recovered", PaymentStatus.SUCCESS, null))

            // when
            reconciler.reconcile()

            // then
            assertThat(paymentService.findByOrderId(orderId)!!.status).isEqualTo(PaymentStatus.SUCCESS)
            assertThat(paymentService.findByOrderId(orderId)!!.transactionKey).isEqualTo("TR-recovered")
            assertThat(inMemoryOrderRepository.findByIdOrNull(orderId)!!.status).isEqualTo(OrderStatus.CONFIRMED)
        }

        @DisplayName("PG 역조회 1건 FAILED 면 결제 FAILED·주문 CANCELLED·재고 복원으로 복구한다.")
        @Test
        fun recoversToFailureAndRestoresStock() {
            // given
            val orderId = givenStalePendingWithoutTxKey(productId = 9L, quantity = 2, stockQty = 8)
            fakePaymentPort.findByOrderIdResult =
                listOf(PaymentResult(orderId, "TR-recovered", PaymentStatus.FAILED, "한도초과"))

            // when
            reconciler.reconcile()

            // then
            assertThat(paymentService.findByOrderId(orderId)!!.status).isEqualTo(PaymentStatus.FAILED)
            assertThat(inMemoryOrderRepository.findByIdOrNull(orderId)!!.status).isEqualTo(OrderStatus.CANCELLED)
            assertThat(inMemoryStockRepository.findStockByProductId(9L)!!.quantity).isEqualTo(10)
        }

        @DisplayName("PG 역조회가 PENDING 이면 전이하지 않고 다음 주기로 미룬다.")
        @Test
        fun skipsWhenPgPending() {
            // given
            val orderId = givenStalePendingWithoutTxKey(productId = 10L)
            fakePaymentPort.findByOrderIdResult =
                listOf(PaymentResult(orderId, "TR-1", PaymentStatus.PENDING, null))

            // when
            reconciler.reconcile()

            // then
            assertThat(paymentService.findByOrderId(orderId)!!.status).isEqualTo(PaymentStatus.PENDING)
        }

        @DisplayName("PG 역조회가 2건 이상(멱등 위반)이면 자동 전이하지 않고 보류한다.")
        @Test
        fun holdsWhenDuplicateDetected() {
            // given
            val orderId = givenStalePendingWithoutTxKey(productId = 11L)
            fakePaymentPort.findByOrderIdResult = listOf(
                PaymentResult(orderId, "TR-1", PaymentStatus.SUCCESS, null),
                PaymentResult(orderId, "TR-2", PaymentStatus.SUCCESS, null),
            )

            // when
            reconciler.reconcile()

            // then
            assertThat(paymentService.findByOrderId(orderId)!!.status).isEqualTo(PaymentStatus.PENDING)
        }
    }
}
