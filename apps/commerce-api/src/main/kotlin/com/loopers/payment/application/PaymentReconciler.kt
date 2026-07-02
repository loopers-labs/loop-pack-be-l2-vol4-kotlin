package com.loopers.payment.application

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class PaymentReconciler(
    private val paymentService: PaymentService,
    private val pgPaymentGateway: PgPaymentGateway,
    private val alertSender: AlertSender,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${pg.reconcile.fixed-delay:60000}")
    fun reconcile() {
        val targets = paymentService.findReconcileTargets()
        if (targets.isEmpty()) {
            return
        }
        logger.info("UNKNOWN 결제 {}건 reconcile 시작", targets.size)
        targets.forEach { target ->
            val queryResult = pgPaymentGateway.query(PgQueryCommand(target.userId, target.orderKey))
            val outcome = paymentService.applyReconcile(target.paymentId, queryResult)
            if (outcome == ReconcileOutcome.NEEDS_ALERT) {
                alertSender.alert(
                    "결제 reconcile 미해소 (paymentId=${target.paymentId}, orderKey=${target.orderKey}): $queryResult",
                )
            }
        }
    }
}
