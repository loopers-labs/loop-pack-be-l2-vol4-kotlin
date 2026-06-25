package com.loopers.batch.job.payment.step

import com.loopers.application.payment.SyncPaymentResultCommand
import com.loopers.application.payment.usecase.SyncPaymentResultUsecase
import com.loopers.batch.job.payment.PaymentReconciliationJobConfig
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PgClient
import com.loopers.domain.payment.PgOrderLookup
import com.loopers.domain.payment.PgStatus
import org.slf4j.LoggerFactory
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

// no @StepScope: reconcile() is invoked directly and uses no step-scoped (@Value jobParameters) params.
@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = PaymentReconciliationJobConfig.JOB_NAME)
@Component
class PaymentReconciliationTasklet(
    private val paymentRepository: PaymentRepository,
    private val pgClient: PgClient,
    private val syncPaymentResultUsecase: SyncPaymentResultUsecase,
) : Tasklet {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        // ponytail: 30초 이상 PENDING 이면 재조회. 운영 임계는 yml 파라미터화 여지 있음.
        private const val STALE_THRESHOLD_SECONDS = 30L
    }

    override fun execute(
        contribution: StepContribution,
        chunkContext: ChunkContext,
    ): RepeatStatus {
        val reflected = reconcile(STALE_THRESHOLD_SECONDS)
        log.info("payment reconciliation: {} reflected", reflected)
        return RepeatStatus.FINISHED
    }

    fun reconcile(thresholdSeconds: Long): Int {
        val threshold = ZonedDateTime.now().minusSeconds(thresholdSeconds)
        var reflected = 0
        paymentRepository.findStalePending(threshold).forEach { payment ->
            // ponytail: Found→반영, NotAccepted/Unknown→skip(no-op). T6에서 종결 정책 추가.
            val result: com.loopers.domain.payment.PgPaymentResult? =
                payment.transactionKey
                    ?.let { pgClient.getByTransactionKey(it) }
                    ?: when (val lookup = pgClient.findByOrderId(payment.orderId)) {
                        is PgOrderLookup.Found -> lookup.result
                        is PgOrderLookup.NotAccepted, is PgOrderLookup.Unknown -> null
                    }
            if (result != null && result.status != PgStatus.PENDING) {
                syncPaymentResultUsecase.apply(
                    SyncPaymentResultCommand(
                        transactionKey = result.transactionKey ?: payment.transactionKey,
                        orderId = payment.orderId,
                        status = result.status,
                        failureReason = result.failureReason,
                    ),
                )
                reflected++
            }
        }
        return reflected
    }
}
