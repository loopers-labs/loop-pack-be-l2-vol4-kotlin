package com.loopers.application.payment

import com.loopers.domain.payment.PaymentEventRepository
import com.loopers.domain.payment.PaymentEventType
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class PaymentApplicationServiceTest @Autowired constructor(
    private val paymentApplicationService: PaymentApplicationService,
    private val paymentRepository: PaymentRepository,
    private val paymentEventRepository: PaymentEventRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Test
    fun createReadyPaymentAppendsRequestCreatedEvent() {
        val payment = paymentApplicationService.createReady(orderId = 1L, requestedAmount = 3000L)

        val events = paymentEventRepository.findByOrderId(1L)
        assertAll(
            { assertThat(payment.status).isEqualTo(PaymentStatus.READY) },
            { assertThat(payment.paymentRequestId).startsWith("order-1-") },
            { assertThat(events.map { it.eventType }).containsExactly(PaymentEventType.REQUEST_CREATED) },
        )
    }

    @Test
    fun recordApproveRequestedStoresPaymentKeyAndAppendsEvent() {
        paymentApplicationService.createReady(orderId = 1L, requestedAmount = 3000L)

        paymentApplicationService.recordApproveRequested(orderId = 1L, paymentKey = "payment-key-1")

        val payment = paymentRepository.findByOrderId(1L)!!
        val events = paymentEventRepository.findByOrderId(1L)
        assertAll(
            { assertThat(payment.paymentKey).isEqualTo("payment-key-1") },
            { assertThat(events.map { it.eventType }).containsExactly(PaymentEventType.REQUEST_CREATED, PaymentEventType.APPROVE_REQUESTED) },
        )
    }

    @Test
    fun recordApproveSucceededApprovesPaymentAndAppendsEvent() {
        paymentApplicationService.createReady(orderId = 1L, requestedAmount = 3000L)
        paymentApplicationService.recordApproveRequested(orderId = 1L, paymentKey = "payment-key-1")

        paymentApplicationService.recordApproveSucceeded(
            orderId = 1L,
            pgTransactionId = "pg-tx-1",
            approvedAmount = 3000L,
            pgStatus = "APPROVED",
            rawResponseSummary = "fake approved",
        )

        val payment = paymentRepository.findByOrderId(1L)!!
        val events = paymentEventRepository.findByOrderId(1L)
        assertAll(
            { assertThat(payment.status).isEqualTo(PaymentStatus.APPROVED) },
            { assertThat(payment.pgTransactionId).isEqualTo("pg-tx-1") },
            { assertThat(events.last().eventType).isEqualTo(PaymentEventType.APPROVE_SUCCEEDED) },
        )
    }
}
