package com.loopers.application.payment

import com.loopers.domain.order.OrderEvent
import com.loopers.domain.order.OrderService
import com.loopers.domain.order.restoreStockOnCancel
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PaymentCommand
import com.loopers.domain.payment.PaymentEvent
import com.loopers.domain.payment.PaymentModel
import com.loopers.domain.payment.PaymentPort
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.stock.StockService
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime
import java.util.UUID

@Component
class PaymentFacade(
    private val paymentService: PaymentService,
    private val orderService: OrderService,
    private val stockService: StockService,
    private val paymentPort: PaymentPort,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun requestPayment(userId: String, orderId: Long, cardType: CardType, cardNumber: String): PaymentModel {
        val amount = orderService.getOrder(orderId).finalAmount.toLong()
        val payment = paymentService.getOrCreatePending(orderId, userId, cardType, amount)
        if (payment.transactionKey != null) return payment

        runCatching {
            paymentPort.requestPayment(PaymentCommand(orderId, userId, cardType, cardNumber, amount))
        }.onSuccess {
            result -> paymentService.addTransactionKey(orderId, result.transactionKey)
        }.onFailure {
            log.warn("PG 결제 요청이 실패하였습니다. 주문ID= {}, reason={}", orderId, it.message)
        }
        return paymentService.findByOrderId(orderId)!!
    }

    @Transactional
    fun confirm(transactionKey: String, status: PaymentStatus, reason: String?) {
        val isSuccessTransaction = paymentService.confirm(
            transactionKey = transactionKey,
            success = status == PaymentStatus.SUCCESS,
            reason = reason,
        )
        if (isSuccessTransaction != true) {
            return
        }

        val orderId = paymentService.findByTransactionKey(transactionKey)!!.orderId
        val order = orderService.getOrder(orderId)
        if (status == PaymentStatus.SUCCESS) {
            order.confirm()
            eventPublisher.publishEvent(
                OrderEvent.Confirmed(
                    eventId = UUID.randomUUID().toString(),
                    orderId = orderId,
                    userId = order.userId,
                    items = order.items.map { OrderEvent.Item(it.productId, it.quantity) },
                    occurredAt = ZonedDateTime.now(),
                ),
            )
            eventPublisher.publishEvent(
                PaymentEvent.Confirmed(eventId = UUID.randomUUID().toString(), orderId = orderId, occurredAt = ZonedDateTime.now()),
            )
        } else {
            val stocks = stockService.findWithLockByProductIdIn(order.items.map { it.productId })
            restoreStockOnCancel(order, stocks)
            eventPublisher.publishEvent(
                PaymentEvent.Failed(eventId = UUID.randomUUID().toString(), orderId = orderId, reason = reason, occurredAt = ZonedDateTime.now()),
            )
        }
    }

    @Transactional
    fun autoFail(orderId: Long, reason: String) {
        if (paymentService.fail(orderId, reason) != true) {
            return
        }
        val order = orderService.getOrder(orderId)
        val stocks = stockService.findWithLockByProductIdIn(order.items.map { it.productId })
        restoreStockOnCancel(order, stocks)
        eventPublisher.publishEvent(
            PaymentEvent.Failed(eventId = UUID.randomUUID().toString(), orderId = orderId, reason = reason, occurredAt = ZonedDateTime.now()),
        )
    }
}
