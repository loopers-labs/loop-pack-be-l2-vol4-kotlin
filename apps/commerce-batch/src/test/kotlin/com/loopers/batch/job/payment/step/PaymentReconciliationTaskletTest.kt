package com.loopers.batch.job.payment.step

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.loopers.application.payment.usecase.SyncPaymentResultUsecase
import com.loopers.domain.payment.PaymentFailureReason
import com.loopers.domain.payment.PaymentModel
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.PgClient
import com.loopers.domain.payment.PgOrderLookup
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime

class PaymentReconciliationTaskletTest {
    private val paymentRepository = mockk<PaymentRepository>()
    private val pgClient = mockk<PgClient>()
    private val syncPaymentResultUsecase = mockk<SyncPaymentResultUsecase>(relaxed = true)

    private val tasklet =
        PaymentReconciliationTasklet(paymentRepository, pgClient, syncPaymentResultUsecase)

    @Test
    fun `tMax 초과 격리에서 CAS가 실패하면(그 사이 콜백이 먼저 종결) ALERT 를 남기지 않고 reflected 도 증가하지 않는다`() {
        // Arrange: PENDING 조회되었으나 조회 결과 불명(Unknown)이라 tMax 판정으로 진입하는 결제
        val payment =
            mockk<PaymentModel> {
                every { transactionKey } returns null
                every { orderId } returns 1L
                every { id } returns 100L
                every { acceptedAt } returns null
                every { createdAt } returns ZonedDateTime.now().minusSeconds(3600)
            }
        every { paymentRepository.findStalePending(any()) } returns listOf(payment)
        every { pgClient.findByOrderId(1L) } returns PgOrderLookup.Unknown
        // CAS 실패: 조회 후 콜백이 먼저 상태를 종결시켜 PENDING 조건이 어긋남 → affected = 0
        every {
            paymentRepository.compareAndSetStatus(100L, PaymentStatus.FAILED, PaymentFailureReason.UNRESOLVED, any())
        } returns 0

        val logger = LoggerFactory.getLogger(PaymentReconciliationTasklet::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)

        // Act: tMax=0 → 즉시 tMax 초과
        val reflected = tasklet.reconcile(0L, 0L)

        // Assert: CAS 실패이므로 상태 전이가 없었고, 경보/집계도 발생하지 않아야 한다
        logger.detachAppender(appender)
        verify(exactly = 1) {
            paymentRepository.compareAndSetStatus(100L, PaymentStatus.FAILED, PaymentFailureReason.UNRESOLVED, any())
        }
        assertThat(reflected).isEqualTo(0)
        assertThat(appender.list.none { it.formattedMessage.contains("[ALERT]") }).isTrue()
    }
}
