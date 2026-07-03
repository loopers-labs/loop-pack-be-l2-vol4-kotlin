package com.loopers.application.payment

import com.loopers.application.order.OrderConfirmService
import com.loopers.application.order.OrderReleaseService
import com.loopers.domain.payment.PaymentRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
class OrphanPaymentReconciliationScheduler(
    private val paymentRepository: PaymentRepository,
    private val paymentApplicationService: PaymentApplicationService,
    private val paymentGateway: PaymentGateway,
    private val orderConfirmService: OrderConfirmService,
    private val orderReleaseService: OrderReleaseService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 600_000)
    fun reconcile() {
        val threshold = ZonedDateTime.now().minusMinutes(5)
        val requestedPayments = paymentRepository.findRequestedOlderThan(threshold)

        for (payment in requestedPayments) {
            try {
                val pgTransactions = paymentGateway.getTransactionsByOrderId(payment.orderId.toString())

                if (pgTransactions.isEmpty()) {
                    paymentApplicationService.markFailedIfRequested(payment.id!!, "PG에 거래 내역 없음")
                    orderReleaseService.markPaymentFailed(payment.orderId)
                    log.info("대사: PG 거래 없음, FAILED 처리. orderId={}", payment.orderId)
                    continue
                }

                val pgTransaction = pgTransactions.first()

                paymentApplicationService.markPgResult(
                    payment.id!!,
                    pgTransaction.transactionKey,
                    pgTransaction.status,
                    pgTransaction.reason,
                )

                when (pgTransaction.status) {
                    PaymentStatus.SUCCESS -> {
                        orderConfirmService.confirm(payment.orderId)
                        log.info("대사: 결제 확정. orderId={}, transactionKey={}", payment.orderId, pgTransaction.transactionKey)
                    }
                    PaymentStatus.FAILED -> {
                        orderReleaseService.markPaymentFailed(payment.orderId)
                        log.info("대사: 결제 실패 처리. orderId={}, transactionKey={}", payment.orderId, pgTransaction.transactionKey)
                    }
                    PaymentStatus.PENDING -> {
                        log.info(
                            "대사: PG 아직 처리 중, PENDING 전환. orderId={}, transactionKey={}",
                            payment.orderId,
                            pgTransaction.transactionKey,
                        )
                    }
                    PaymentStatus.REQUESTED -> {
                        log.warn("대사: PG에서 REQUESTED 상태 반환. orderId={}", payment.orderId)
                    }
                }
            } catch (e: Exception) {
                log.warn("대사: REQUESTED 결제 복구 실패. paymentId={}, orderId={}", payment.id, payment.orderId, e)
            }
        }
    }
}
