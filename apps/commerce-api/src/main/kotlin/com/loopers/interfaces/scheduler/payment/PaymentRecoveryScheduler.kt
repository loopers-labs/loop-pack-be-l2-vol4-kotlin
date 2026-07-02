package com.loopers.interfaces.scheduler.payment

import com.loopers.application.payment.PaymentFacade
import com.loopers.domain.payment.PaymentService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

/**
 * 콜백 유실/요청 타임아웃으로 PENDING 에 머무는 결제건을 주기적으로 PG 와 동기화한다.
 */
@Component
class PaymentRecoveryScheduler(
    private val paymentService: PaymentService,
    private val paymentFacade: PaymentFacade,
) {
    @Scheduled(fixedDelayString = "\${pg.recovery.fixed-delay-ms:30000}")
    fun recoverStalePayments() {
        val threshold = ZonedDateTime.now().minusSeconds(STALE_THRESHOLD_SECONDS)
        val stalePayments = paymentService.findStalePending(threshold)
        if (stalePayments.isEmpty()) return

        logger.info("PENDING 결제 동기화 시작. 대상={}건", stalePayments.size)
        stalePayments.forEach { payment ->
            runCatching { paymentFacade.sync(payment.orderId) }
                .onFailure { e -> logger.warn("결제 동기화 실패. orderId={}, cause={}", payment.orderId, e.message) }
        }
    }

    companion object {
        private const val STALE_THRESHOLD_SECONDS = 30L
        private val logger = LoggerFactory.getLogger(PaymentRecoveryScheduler::class.java)
    }
}
