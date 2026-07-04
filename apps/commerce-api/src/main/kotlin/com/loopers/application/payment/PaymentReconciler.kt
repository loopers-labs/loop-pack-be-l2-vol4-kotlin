package com.loopers.application.payment

import com.loopers.domain.payment.PaymentPort
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PaymentStatus
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.ZonedDateTime

@Component
class PaymentReconciler(
    private val paymentService: PaymentService,
    private val paymentFacade: PaymentFacade,
    @Value("\${payment.reconcile.stale-threshold}")
    private val staleThreshold: Duration,
    private val paymentPort: PaymentPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${payment.reconcile.interval}")
    fun reconcile() {
        val threshold = ZonedDateTime.now().minus(staleThreshold)
        paymentService.getAllByPendingAndCreatedAtBefore(threshold).forEach { payment ->
            runCatching {
                val transactionKey = payment.transactionKey
                if (transactionKey == null) {
                    val results = paymentPort.getAllByOrderId(payment.userId, payment.orderId)
                    when {
                        results.size >= 2 -> log.error("[reconcile] 멱등 위반 의심 : orderId = {} PG 거래 {} 건 ", payment.orderId, results.size)
                        results.size == 1 -> {
                            val found = results.first()
                            when (found.status) {
                                PaymentStatus.SUCCESS, PaymentStatus.FAILED -> {
                                    paymentService.addTransactionKey(payment.orderId, found.transactionKey)
                                    paymentFacade.confirm(found.transactionKey, found.status, found.reason)
                                }
                                PaymentStatus.PENDING -> Unit // PG 처리 중
                            }
                        }
                        else -> {
                            paymentFacade.autoFail(payment.orderId, "PG 미접수 (no transaction)")
                            log.warn("[reconcile] PG 미접수 확인 orderId={} -> autoFail", payment.orderId)
                        }
                    }
                } else {
                    val result = paymentPort.getTransaction(payment.userId, transactionKey)
                    when (result.status) {
                        PaymentStatus.SUCCESS, PaymentStatus.FAILED -> paymentFacade.confirm(transactionKey, result.status, result.reason)
                        PaymentStatus.PENDING -> Unit // PG 처리 중
                    }
                }
            }.onFailure { log.warn("[reconcile] 거래 처리 실패 orderId = {}, {}", payment.orderId, it.message) }
        }
    }
}
