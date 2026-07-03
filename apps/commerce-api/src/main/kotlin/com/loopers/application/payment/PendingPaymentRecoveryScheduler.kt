package com.loopers.application.payment

import com.loopers.domain.payment.PaymentRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
class PendingPaymentRecoveryScheduler(
    private val paymentRepository: PaymentRepository,
    private val paymentGateway: PaymentGateway,
    private val paymentFacade: PaymentFacade,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 300_000)
    fun recover() {
        val threshold = ZonedDateTime.now().minusMinutes(5)
        val pendingPayments = paymentRepository.findPendingOlderThan(threshold)

        for (payment in pendingPayments) {
            try {
                val transactionKey = payment.transactionKey ?: continue
                val pgStatus = paymentGateway.getTransactionStatus(transactionKey)

                if (pgStatus.status == PaymentStatus.PENDING) {
                    continue
                }

                paymentFacade.handleCallback(
                    PaymentCallbackCommand(
                        transactionKey = transactionKey,
                        status = pgStatus.status,
                        reason = pgStatus.reason,
                    ),
                )
                log.info("PENDING 결제 복구 완료: transactionKey={}, status={}", transactionKey, pgStatus.status)
            } catch (e: Exception) {
                log.warn("PENDING 결제 복구 실패: transactionKey={}", payment.transactionKey, e)
            }
        }
    }
}
