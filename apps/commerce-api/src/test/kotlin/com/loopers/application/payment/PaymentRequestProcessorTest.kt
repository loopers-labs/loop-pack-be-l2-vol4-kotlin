package com.loopers.application.payment

import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.context.ApplicationEventPublisher
import java.time.ZonedDateTime

class PaymentRequestProcessorTest {
    private lateinit var paymentRepository: FakePaymentRepository
    private lateinit var fakePaymentGateway: FakePaymentGateway
    private lateinit var paymentRequestProcessor: PaymentRequestProcessor

    @BeforeEach
    fun setUp() {
        paymentRepository = FakePaymentRepository()
        fakePaymentGateway = FakePaymentGateway()
        paymentRequestProcessor = PaymentRequestProcessor(
            paymentApplicationService = PaymentApplicationService(
                paymentRepository = paymentRepository,
                eventPublisher = NoOpEventPublisher,
            ),
            paymentGateway = fakePaymentGateway,
        )
    }

    @DisplayName("REQUESTED 결제 요청을 PG에 전달하고 PG 응답을 Payment에 반영한다.")
    @Test
    fun process_requestsPgAndMarksPgResult_whenPaymentIsRequested() {
        // arrange
        val payment = paymentRepository.save(newRequestedPayment())
        fakePaymentGateway.nextResult = PaymentResult(
            transactionKey = "20260702:TR:payment-requested",
            status = PaymentStatus.PENDING,
            reason = "결제 요청 접수",
        )

        // act
        paymentRequestProcessor.process(
            paymentId = payment.id!!,
            callbackUrl = "http://localhost:8080/api/v1/payments/callback",
        )

        // assert
        val updatedPayment = paymentRepository.findByTransactionKey("20260702:TR:payment-requested")!!
        assertAll(
            { assertThat(fakePaymentGateway.payCallCount).isEqualTo(1) },
            { assertThat(fakePaymentGateway.lastCommand?.orderId).isEqualTo(payment.orderId) },
            { assertThat(fakePaymentGateway.lastCommand?.userId).isEqualTo(payment.userId) },
            { assertThat(fakePaymentGateway.lastCommand?.amount?.amount).isEqualTo(payment.amount) },
            { assertThat(fakePaymentGateway.lastCommand?.cardType).isEqualTo(payment.cardType) },
            { assertThat(fakePaymentGateway.lastCommand?.cardNo).isEqualTo(payment.cardNo) },
            { assertThat(updatedPayment.status).isEqualTo(PaymentStatus.PENDING) },
            { assertThat(updatedPayment.transactionKey).isEqualTo("20260702:TR:payment-requested") },
            { assertThat(updatedPayment.reason).isEqualTo("결제 요청 접수") },
        )
    }

    @DisplayName("이미 REQUESTED가 아닌 결제 요청은 PG 호출 없이 건너뛴다.")
    @Test
    fun process_skipsPgRequest_whenPaymentIsAlreadyProcessed() {
        // arrange
        val payment = paymentRepository.save(newPendingPayment())

        // act
        paymentRequestProcessor.process(
            paymentId = payment.id!!,
            callbackUrl = "http://localhost:8080/api/v1/payments/callback",
        )

        // assert
        val savedPayment = paymentRepository.findByTransactionKey("20260702:TR:already-pending")!!
        assertAll(
            { assertThat(fakePaymentGateway.payCallCount).isZero() },
            { assertThat(savedPayment.status).isEqualTo(PaymentStatus.PENDING) },
            { assertThat(savedPayment.transactionKey).isEqualTo("20260702:TR:already-pending") },
        )
    }

    private fun newRequestedPayment() = Payment(
        orderId = 1L,
        userId = 1L,
        cardType = "SAMSUNG",
        cardNo = "1234-5678-9012-3456",
        amount = 10_000L,
        status = PaymentStatus.REQUESTED,
    )

    private fun newPendingPayment() = Payment(
        orderId = 2L,
        userId = 1L,
        transactionKey = "20260702:TR:already-pending",
        cardType = "SAMSUNG",
        cardNo = "1234-5678-9012-3456",
        amount = 20_000L,
        status = PaymentStatus.PENDING,
    )

    object NoOpEventPublisher : ApplicationEventPublisher {
        override fun publishEvent(event: Any) = Unit
    }

    class FakePaymentGateway : PaymentGateway {
        var payCallCount: Int = 0
            private set
        var lastCommand: PaymentCommand? = null
            private set
        var nextResult: PaymentResult = PaymentResult(
            transactionKey = "20260702:TR:default",
            status = PaymentStatus.PENDING,
            reason = null,
        )

        override fun pay(command: PaymentCommand): PaymentResult {
            payCallCount += 1
            lastCommand = command
            return nextResult
        }

        override fun getTransactionStatus(transactionKey: String): PaymentTransactionInfo {
            throw UnsupportedOperationException("테스트에서 사용하지 않습니다.")
        }

        override fun getTransactionsByOrderId(orderId: String): List<PaymentTransactionInfo> {
            return emptyList()
        }
    }

    class FakePaymentRepository : PaymentRepository {
        private val payments = linkedMapOf<Long, Payment>()
        private var sequence = 0L

        override fun save(payment: Payment): Payment {
            val savedPayment = payment.copyWithId(payment.id ?: ++sequence)
            payments[savedPayment.id!!] = savedPayment
            return savedPayment
        }

        override fun findById(id: Long): Payment? {
            return payments[id]
        }

        override fun findByTransactionKey(transactionKey: String): Payment? {
            return payments.values.firstOrNull { it.transactionKey == transactionKey }
        }

        override fun findByOrderId(orderId: Long): Payment? {
            return payments.values.firstOrNull { it.orderId == orderId }
        }

        override fun findInProgressByOrderId(orderId: Long): Payment? {
            return payments.values.firstOrNull {
                it.orderId == orderId && it.status in listOf(PaymentStatus.REQUESTED, PaymentStatus.PENDING)
            }
        }

        override fun findRequestedOlderThan(threshold: ZonedDateTime): List<Payment> {
            return payments.values.filter { it.status == PaymentStatus.REQUESTED }
        }

        override fun findPendingOlderThan(threshold: ZonedDateTime): List<Payment> {
            return payments.values.filter { it.status == PaymentStatus.PENDING }
        }

        override fun markPgResultIfRequested(
            id: Long,
            transactionKey: String,
            status: PaymentStatus,
            reason: String?,
        ): Boolean {
            val payment = payments[id] ?: return false
            if (payment.status != PaymentStatus.REQUESTED) {
                return false
            }
            payments[id] = payment.copyWith(
                transactionKey = transactionKey,
                status = status,
                reason = reason,
            )
            return true
        }

        override fun markFailedIfRequested(id: Long, reason: String?): Boolean {
            val payment = payments[id] ?: return false
            if (payment.status != PaymentStatus.REQUESTED) {
                return false
            }
            payments[id] = payment.copyWith(status = PaymentStatus.FAILED, reason = reason)
            return true
        }

        override fun markSuccessIfPending(transactionKey: String, reason: String?): Boolean {
            return updateByTransactionKeyIfCurrent(transactionKey, PaymentStatus.PENDING, PaymentStatus.SUCCESS, reason)
        }

        override fun markFailedIfPending(transactionKey: String, reason: String?): Boolean {
            return updateByTransactionKeyIfCurrent(transactionKey, PaymentStatus.PENDING, PaymentStatus.FAILED, reason)
        }

        private fun updateByTransactionKeyIfCurrent(
            transactionKey: String,
            currentStatus: PaymentStatus,
            targetStatus: PaymentStatus,
            reason: String?,
        ): Boolean {
            val payment = findByTransactionKey(transactionKey) ?: return false
            if (payment.status != currentStatus) {
                return false
            }
            payments[payment.id!!] = payment.copyWith(status = targetStatus, reason = reason)
            return true
        }

        private fun Payment.copyWithId(id: Long): Payment = copyWith(id = id)

        private fun Payment.copyWith(
            id: Long? = this.id,
            transactionKey: String? = this.transactionKey,
            status: PaymentStatus = this.status,
            reason: String? = this.reason,
        ): Payment = Payment(
            id = id,
            orderId = orderId,
            userId = userId,
            transactionKey = transactionKey,
            cardType = cardType,
            cardNo = cardNo,
            amount = amount,
            status = status,
            reason = reason,
        )
    }
}
