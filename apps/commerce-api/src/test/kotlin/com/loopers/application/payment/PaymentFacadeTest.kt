package com.loopers.application.payment

import com.loopers.domain.order.InMemoryOrderRepository
import com.loopers.domain.order.OrderItemModel
import com.loopers.domain.order.OrderModel
import com.loopers.domain.order.OrderService
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.InMemoryPaymentRepository
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

class PaymentFacadeTest {
    private val inMemoryPaymentRepository = InMemoryPaymentRepository()
    private val inMemoryOrderRepository = InMemoryOrderRepository()
    private val inMemoryStockRepository = InMemoryStockRepository()
    private val fakePaymentPort = FakePaymentPort()

    private val paymentService = PaymentService(inMemoryPaymentRepository)
    private val orderService = OrderService(inMemoryOrderRepository)
    private val stockService = StockService(inMemoryStockRepository)
    private val paymentFacade = PaymentFacade(paymentService, orderService, stockService, fakePaymentPort, ApplicationEventPublisher {})

    @Nested
    @DisplayName("결제를 요청할 때")
    inner class RequestPayment {
        @DisplayName("정상 요청이면 PENDING 상태로 접수되고 transactionKey 가 부여된다.")
        @Test
        fun returnsPendingWithTransactionKey() {
            // given
            val order = inMemoryOrderRepository.save(
                OrderModel.of(1L, listOf(OrderItemModel.of(1L, "상품", 1000.0, 2))),
            )

            // when
            val payment = paymentFacade.requestPayment("user1", order.id, CardType.SAMSUNG, "1234-1234-1234-1234")

            // then
            assertThat(payment.status).isEqualTo(PaymentStatus.PENDING)
            assertThat(payment.transactionKey).isEqualTo("TR-${order.id}")
        }

        @DisplayName("PG 호출이 실패해도 예외 없이 PENDING(transactionKey=null) 으로 유지된다.")
        @Test
        fun keepsPendingWhenPgFails() {
            // given
            fakePaymentPort.pay = { throw RuntimeException("PG 500") }
            val order = inMemoryOrderRepository.save(
                OrderModel.of(1L, listOf(OrderItemModel.of(2L, "상품", 1000.0, 2))),
            )

            // when
            val payment = paymentFacade.requestPayment("user1", order.id, CardType.SAMSUNG, "1234-1234-1234-1234")

            // then
            assertThat(payment.status).isEqualTo(PaymentStatus.PENDING)
            assertThat(payment.transactionKey).isNull()
        }

        @DisplayName("이미 transactionKey 가 부여된 주문은 PG 를 재호출하지 않는다.")
        @Test
        fun doesNotRecallPgWhenAlreadyRequested() {
            // given
            val order = inMemoryOrderRepository.save(
                OrderModel.of(1L, listOf(OrderItemModel.of(3L, "상품", 1000.0, 2))),
            )
            paymentFacade.requestPayment("user1", order.id, CardType.SAMSUNG, "1234-1234-1234-1234")

            // when
            val again = paymentFacade.requestPayment("user1", order.id, CardType.SAMSUNG, "1234-1234-1234-1234")

            // then
            assertThat(again.transactionKey).isEqualTo("TR-${order.id}")
        }
    }

    @Nested
    @DisplayName("결제를 확정할 때")
    inner class Confirm {
        private fun setupPendingPayment(productId: Long, quantity: Int, stockQty: Int): Long {
            inMemoryStockRepository.save(StockModel.of(productId, stockQty))
            val order = inMemoryOrderRepository.save(
                OrderModel.of(1L, listOf(OrderItemModel.of(productId, "상품", 1000.0, quantity))),
            )
            paymentFacade.requestPayment("user1", order.id, CardType.SAMSUNG, "1234-1234-1234-1234")
            return order.id
        }

        @DisplayName("SUCCESS 확정이면 결제는 SUCCESS, 주문은 CONFIRMED 가 된다.")
        @Test
        fun confirmSuccess() {
            // given
            val orderId = setupPendingPayment(productId = 1L, quantity = 2, stockQty = 10)
            val txKey = paymentService.findByOrderId(orderId)!!.transactionKey!!

            // when
            paymentFacade.confirm(txKey, PaymentStatus.SUCCESS, "정상 승인")

            // then
            assertThat(paymentService.findByOrderId(orderId)!!.status).isEqualTo(PaymentStatus.SUCCESS)
            assertThat(inMemoryOrderRepository.findByIdOrNull(orderId)!!.status).isEqualTo(OrderStatus.CONFIRMED)
        }

        @DisplayName("FAILED 확정이면 결제는 FAILED, 주문은 CANCELLED, 재고는 복원된다.")
        @Test
        fun confirmFailureRestoresStock() {
            // given: 재고 10 → 주문 2개 (단, placeOrder 가 아니라 직접 저장이라 재고 차감은 안 됨)
            val orderId = setupPendingPayment(productId = 2L, quantity = 2, stockQty = 8)
            val txKey = paymentService.findByOrderId(orderId)!!.transactionKey!!

            // when
            paymentFacade.confirm(txKey, PaymentStatus.FAILED, "한도초과")

            // then
            assertThat(paymentService.findByOrderId(orderId)!!.status).isEqualTo(PaymentStatus.FAILED)
            assertThat(inMemoryOrderRepository.findByIdOrNull(orderId)!!.status).isEqualTo(OrderStatus.CANCELLED)
            // 재고 복원: 8 → 10 (quantity 2 만큼 restore)
            assertThat(inMemoryStockRepository.findStockByProductId(2L)!!.quantity).isEqualTo(10)
        }

        @DisplayName("이미 확정된 결제를 다시 confirm 하면 no-op 이다. (멱등)")
        @Test
        fun confirmIsIdempotent() {
            // given
            val orderId = setupPendingPayment(productId = 3L, quantity = 1, stockQty = 5)
            val txKey = paymentService.findByOrderId(orderId)!!.transactionKey!!
            paymentFacade.confirm(txKey, PaymentStatus.SUCCESS, "정상 승인")

            // when: 두 번째 confirm (FAILED 로 시도) → no-op 이어야 함
            paymentFacade.confirm(txKey, PaymentStatus.FAILED, "한도초과")

            // then: 첫 확정(SUCCESS/CONFIRMED) 그대로
            assertThat(paymentService.findByOrderId(orderId)!!.status).isEqualTo(PaymentStatus.SUCCESS)
            assertThat(inMemoryOrderRepository.findByIdOrNull(orderId)!!.status).isEqualTo(OrderStatus.CONFIRMED)
        }
    }
}
