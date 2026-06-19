package com.loopers.job.order

import com.loopers.batch.job.order.OrderReservationExpirationJobConfig
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
@TestPropertySource(properties = ["spring.batch.job.name=${OrderReservationExpirationJobConfig.JOB_NAME}"])
class OrderReservationExpirationJobE2ETest @Autowired constructor(
    private val jobLauncherTestUtils: JobLauncherTestUtils,
    @param:Qualifier(OrderReservationExpirationJobConfig.JOB_NAME) private val job: Job,
    namedParameterJdbcTemplate: NamedParameterJdbcTemplate,
) {
    private val database = OrderBatchTestDatabase(namedParameterJdbcTemplate)

    @BeforeEach
    fun setUp() {
        jobLauncherTestUtils.job = job
        database.resetSchema()
    }

    @DisplayName("만료된 결제대기 주문 예약을 만료하고 예약 재고를 해제한다.")
    @Test
    fun expiresPendingOrderReservationAndReleasesReservedStock() {
        val orderId = 1001L
        val paymentId = 2001L
        val reservationId = 3001L
        val productId = 4001L
        database.insertProductStock(productId = productId, stockQuantity = 5, reservedQuantity = 2)
        database.insertOrder(orderId = orderId, status = "PAYMENT_PENDING", reservationExpiresAt = LocalDateTime.now().minusMinutes(1))
        database.insertReservation(
            reservationId = reservationId,
            orderId = orderId,
            productId = productId,
            quantity = 2,
            status = "IN_PROGRESS",
        )
        database.insertPayment(paymentId = paymentId, orderId = orderId, status = "READY", requestedAmount = 258000L)

        val jobExecution = jobLauncherTestUtils.launchJob(
            JobParametersBuilder()
                .addLong("run.id", System.nanoTime())
                .toJobParameters(),
        )

        assertAll(
            { assertThat(jobExecution.exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode) },
            { assertThat(database.orderStatus(orderId)).isEqualTo("EXPIRED") },
            { assertThat(database.paymentStatus(orderId)).isEqualTo("EXPIRED") },
            { assertThat(database.reservationStatus(orderId)).isEqualTo("EXPIRED") },
            { assertThat(database.reservedQuantity(productId)).isZero() },
        )
    }
}
