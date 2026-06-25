package com.loopers.application.payment

import com.loopers.domain.order.OrderRepository
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
class OrphanPaymentReconciliationScheduler(
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentRepository,
    private val paymentGateway: PaymentGateway,
    private val paymentFacade: PaymentFacade,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 600_000)
    fun reconcile() {
        val threshold = ZonedDateTime.now().minusMinutes(5)
        val pendingOrders = orderRepository.findPendingPaymentOlderThan(threshold)

        for (order in pendingOrders) {
            try {
                val existingPayment = paymentRepository.findByOrderId(order.id!!)
                if (existingPayment != null) {
                    continue
                }

                val pgTransactions = paymentGateway.getTransactionsByOrderId(order.id.toString())
                if (pgTransactions.isEmpty()) {
                    continue
                }

                val pgTransaction = pgTransactions.first()

                val payment = paymentRepository.save(
                    Payment(
                        orderId = order.id,
                        userId = order.userId,
                        transactionKey = pgTransaction.transactionKey,
                        cardType = pgTransaction.cardType,
                        cardNo = pgTransaction.cardNo,
                        amount = pgTransaction.amount,
                        status = PaymentStatus.PENDING,
                    ),
                )

                if (pgTransaction.status == PaymentStatus.PENDING) {
                    log.info("대사: PG 거래 발견, 아직 PENDING. orderId={}, transactionKey={}", order.id, pgTransaction.transactionKey)
                    continue
                }

                paymentFacade.handleCallback(
                    PaymentCallbackCommand(
                        transactionKey = payment.transactionKey,
                        status = pgTransaction.status,
                        reason = pgTransaction.reason,
                    ),
                )
                log.info(
                    "대사: 유령 결제 복구 완료. orderId={}, transactionKey={}, status={}",
                    order.id,
                    pgTransaction.transactionKey,
                    pgTransaction.status,
                )
            } catch (e: Exception) {
                log.warn("대사: 주문 복구 실패. orderId={}", order.id, e)
            }
        }
    }
}
