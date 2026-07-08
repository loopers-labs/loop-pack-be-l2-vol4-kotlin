package com.loopers.application.payment

import com.loopers.application.order.OrderCancellation
import com.loopers.application.outbox.OutboxFactory
import com.loopers.application.outbox.OutboxSavedEvent
import com.loopers.application.outbox.OutboxWriter
import com.loopers.domain.order.OrderService
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.outbox.ProductMetricType
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentGatewayPort
import com.loopers.domain.payment.PaymentGatewayRequest
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PaymentStatus
import com.loopers.interfaces.api.payment.PaymentApplicationServicePort
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentApplicationServiceAdapter(
    private val orderService: OrderService,
    private val paymentService: PaymentService,
    private val paymentGatewayPort: PaymentGatewayPort,
    private val paymentInitiation: PaymentInitiation,
    private val orderCancellation: OrderCancellation,
    private val eventPublisher: ApplicationEventPublisher,
    private val outboxWriter: OutboxWriter,
    private val outboxFactory: OutboxFactory,
) : PaymentApplicationServicePort {

    override fun pay(command: PayCommand): PaymentResult {
        val order = orderService.getById(command.orderId)
        if (order.userId != command.userId) {
            throw CoreException(ErrorType.FORBIDDEN, "본인의 주문만 결제할 수 있습니다.")
        }
        if (order.status != OrderStatus.CREATED) {
            throw CoreException(ErrorType.CONFLICT, "결제할 수 없는 주문 상태입니다: ${order.status}")
        }
        val amount = order.getActualAmount()

        // 외부 PG 호출은 트랜잭션 밖에서 수행한다.
        val gatewayResponse = paymentGatewayPort.requestPayment(
            PaymentGatewayRequest(
                userId = command.userId,
                orderId = order.id,
                cardType = command.cardType,
                cardNo = command.cardNo,
                amount = amount,
            ),
        )

        val payment = paymentInitiation.initiate(order, command, amount, gatewayResponse.transactionKey)
        return PaymentResult.from(payment)
    }

    @Transactional
    override fun handleCallback(command: PaymentCallbackCommand) {
        val payment = paymentService.getByTransactionKey(command.transactionKey)
        if (payment.status != PaymentStatus.PENDING) {
            // 이미 종료(SUCCESS/FAILED)된 결제건의 중복 콜백은 멱등하게 무시한다.
            return
        }
        applyResult(payment, command.status, command.reason)
    }

    /**
     * 콜백이 오지 않은(또는 응답 타임아웃으로 미확정인) 결제건을 PG 에 직접 조회해 상태를 복구한다.
     * 일정 주기 배치 대신 수동 API 로 트리거한다. 이미 종료된 건은 멱등하게 그대로 반환한다.
     */
    @Transactional
    override fun reconcile(transactionKey: String): PaymentResult {
        val payment = paymentService.getByTransactionKey(transactionKey)
        if (payment.status == PaymentStatus.PENDING) {
            // PG 에 거래가 아직 없으면(null) 미확정으로 보고 다음 복구 시도에 맡긴다.
            paymentGatewayPort.getTransaction(payment.userId, transactionKey)
                ?.let { applyResult(payment, it.status, it.reason) }
        }
        return PaymentResult.from(paymentService.getByTransactionKey(transactionKey))
    }

    /** 콜백·복구가 공유하는 결과 반영. PENDING 결제건에만 호출된다(호출자가 가드). */
    private fun applyResult(payment: Payment, status: PaymentStatus, reason: String?) {
        when (status) {
            PaymentStatus.SUCCESS -> {
                val approvedPayment = paymentService.save(payment.approve())
                val order = orderService.getById(payment.orderId)
                orderService.save(order.updateStatus(OrderStatus.PAYMENT_COMPLETED))
                order.items.productIds().forEach { productId ->
                    val outbox = outboxWriter.save(
                        outboxFactory.createProductMetricOutbox(productId, ProductMetricType.SALES, 1L),
                    )
                    eventPublisher.publishEvent(OutboxSavedEvent(outbox))
                }
                eventPublisher.publishEvent(
                    PaymentCompletedEvent(
                        paymentId = approvedPayment.id,
                        orderId = approvedPayment.orderId,
                        userId = approvedPayment.userId,
                    ),
                )
            }
            PaymentStatus.FAILED -> {
                paymentService.save(payment.fail(reason))
                // 재고·쿠폰 원복 + 주문 취소 (보상 트랜잭션)
                orderCancellation.cancel(payment.orderId)
            }
            // 중간 통지(PENDING)는 최종 결과를 기다리며 무시한다.
            PaymentStatus.PENDING -> Unit
        }
    }
}
