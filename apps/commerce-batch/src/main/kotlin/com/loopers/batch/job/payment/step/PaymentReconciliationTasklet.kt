package com.loopers.batch.job.payment.step

import com.loopers.application.payment.SyncPaymentResultCommand
import com.loopers.application.payment.usecase.SyncPaymentResultUsecase
import com.loopers.batch.job.payment.PaymentReconciliationJobConfig
import com.loopers.domain.payment.PaymentFailureReason
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
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

        // ponytail: 10분 초과 PENDING 불명 → UNRESOLVED 격리. 운영 임계는 yml 파라미터화 여지 있음.
        private const val T_MAX_SECONDS = 600L
    }

    override fun execute(
        contribution: StepContribution,
        chunkContext: ChunkContext,
    ): RepeatStatus {
        val reflected = reconcile(STALE_THRESHOLD_SECONDS, T_MAX_SECONDS)
        log.info("payment reconciliation: {} reflected", reflected)
        return RepeatStatus.FINISHED
    }

    fun reconcile(thresholdSeconds: Long, tMaxSeconds: Long): Int {
        val now = ZonedDateTime.now()
        val threshold = now.minusSeconds(thresholdSeconds)
        var reflected = 0

        paymentRepository.findStalePending(threshold).forEach { payment ->
            var terminated = false

            if (payment.transactionKey != null) {
                // txKey 알고 있음 → 직접 조회
                val r = pgClient.getByTransactionKey(payment.transactionKey!!)
                if (r.status != PgStatus.PENDING) {
                    apply(r.transactionKey ?: payment.transactionKey, payment.orderId, r.status, r.failureReason)
                    reflected++
                    terminated = true
                }
                // PENDING → stays pending, falls through to tMax check
            } else {
                // txKey 없음 → orderId 조회
                when (val lookup = pgClient.findByOrderId(payment.orderId)) {
                    is PgOrderLookup.Found -> {
                        val r = lookup.result
                        if (r.status != PgStatus.PENDING) {
                            apply(r.transactionKey, payment.orderId, r.status, r.failureReason)
                            reflected++
                            terminated = true
                        }
                        // PENDING → falls through
                    }
                    is PgOrderLookup.NotAccepted -> {
                        // PG 미접수 확정 → FAILED(NOT_ACCEPTED) + 보상
                        apply(null, payment.orderId, PgStatus.FAILED, PaymentFailureReason.NOT_ACCEPTED)
                        reflected++
                        terminated = true
                    }
                    is PgOrderLookup.Unknown -> {
                        // 조회 불명 → tMax 판정으로 넘어감
                    }
                }
            }

            if (!terminated) {
                // tMax 판정: acceptedAt 없으면 createdAt 기준
                val baseline = payment.acceptedAt ?: payment.createdAt
                if (baseline.isBefore(now.minusSeconds(tMaxSeconds))) {
                    // UNRESOLVED 격리 — 주문 전이/보상 없음.
                    // CAS(PENDING 조건)가 실제로 1건 전이했을 때만 경보/카운트 — 그 사이 콜백이
                    // 먼저 종결(affected=0)했다면 false ALERT 와 reflected 과다 집계를 막는다.
                    val affected =
                        paymentRepository.compareAndSetStatus(payment.id, PaymentStatus.FAILED, PaymentFailureReason.UNRESOLVED, now)
                    if (affected == 1) {
                        log.warn(
                            "[ALERT] Payment {} (orderId={}) exceeded tMax and is UNRESOLVED — manual intervention required",
                            payment.id,
                            payment.orderId,
                        )
                        reflected++
                    }
                } else {
                    // 정상 폴링 추적 — 트랜잭션 밖에서 조회된 detached 엔티티를 save(merge)하면
                    // 그 사이 도착한 콜백의 종결 상태를 PENDING 으로 덮어쓸 수 있으므로(Lost Update),
                    // PENDING 인 건에 한해 추적 컬럼만 벌크 업데이트한다.
                    paymentRepository.incrementPollAttempts(payment.id, now)
                }
            }
        }
        return reflected
    }

    private fun apply(
        transactionKey: String?,
        orderId: Long,
        status: PgStatus,
        failureReason: PaymentFailureReason?,
    ) {
        syncPaymentResultUsecase.apply(
            SyncPaymentResultCommand(
                transactionKey = transactionKey,
                orderId = orderId,
                amount = null,
                status = status,
                failureReason = failureReason,
            ),
        )
    }
}
