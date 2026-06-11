package com.loopers.job.order

import com.loopers.batch.job.order.PaymentCompletionRetryJobConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.test.JobLauncherTestUtils
import org.springframework.batch.test.context.SpringBatchTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.context.TestPropertySource
import java.time.LocalDateTime

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = ["spring.batch.job.name=${PaymentCompletionRetryJobConfig.JOB_NAME}"])
class PaymentCompletionRetryJobE2ETest @Autowired constructor(
    private val jobLauncherTestUtils: JobLauncherTestUtils,
    @param:Qualifier(PaymentCompletionRetryJobConfig.JOB_NAME) private val job: Job,
    namedParameterJdbcTemplate: NamedParameterJdbcTemplate,
) {
    private val database = OrderBatchTestDatabase(namedParameterJdbcTemplate)

    @BeforeEach
    fun setUp() {
        jobLauncherTestUtils.job = job
        database.resetSchema()
    }

    @DisplayName("완료 실패 결제를 재시도해 예약을 확정하고 주문을 완료한다.")
    @Test
    fun retriesCompletionFailedPaymentAndCompletesOrder() {
        val orderId = 1002L
        val paymentId = 2002L
        val reservationId = 3002L
        val productId = 4002L
        database.insertProductStock(productId = productId, stockQuantity = 5, reservedQuantity = 2)
        database.insertOrder(orderId = orderId, status = "FAILED", reservationExpiresAt = LocalDateTime.now().minusMinutes(30))
        database.insertReservation(
            reservationId = reservationId,
            orderId = orderId,
            productId = productId,
            quantity = 2,
            status = "IN_PROGRESS",
        )
        database.insertPayment(
            paymentId = paymentId,
            orderId = orderId,
            status = "COMPLETION_FAILED",
            requestedAmount = 258000L,
            paymentKey = "payment-key-$orderId",
            pgTransactionId = "payment-$orderId",
            approvedAmount = 258000L,
            failureReason = "internal completion failed",
        )

        val jobExecution = jobLauncherTestUtils.launchJob(
            JobParametersBuilder()
                .addLong("run.id", System.nanoTime())
                .toJobParameters(),
        )

        assertAll(
            { assertThat(jobExecution.exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode) },
            { assertThat(database.orderStatus(orderId)).isEqualTo("COMPLETED") },
            { assertThat(database.paymentStatus(orderId)).isEqualTo("APPROVED") },
            { assertThat(database.reservationStatus(orderId)).isEqualTo("COMPLETED") },
            { assertThat(database.stockQuantity(productId)).isEqualTo(3) },
            { assertThat(database.reservedQuantity(productId)).isZero() },
        )
    }
}
