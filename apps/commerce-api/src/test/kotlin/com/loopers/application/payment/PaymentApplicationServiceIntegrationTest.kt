package com.loopers.application.payment

import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class PaymentApplicationServiceIntegrationTest @Autowired constructor(
    private val paymentApplicationService: PaymentApplicationService,
    private val paymentRepository: PaymentRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("결제 성공 처리 시, ")
    @Nested
    inner class MarkSuccess {
        @DisplayName("PENDING 상태의 결제를 SUCCESS로 변경한다.")
        @Test
        fun markSuccess_whenPending() {
            // arrange
            val payment = paymentRepository.save(newPendingPayment())

            // act
            val result = paymentApplicationService.markSuccess(
                payment.transactionKey!!,
                "정상 승인되었습니다.",
            )

            // assert
            assertThat(result.status).isEqualTo(PaymentStatus.SUCCESS)
            assertThat(result.reason).isEqualTo("정상 승인되었습니다.")
        }

        @DisplayName("이미 SUCCESS 상태인 결제에 다시 성공 처리하면 CONFLICT 예외가 발생한다.")
        @Test
        fun throwsConflict_whenAlreadySuccess() {
            // arrange
            val payment = paymentRepository.save(newPendingPayment())
            paymentApplicationService.markSuccess(payment.transactionKey!!, "정상 승인되었습니다.")

            // act & assert
            val result = assertThrows<CoreException> {
                paymentApplicationService.markSuccess(payment.transactionKey!!, "정상 승인되었습니다.")
            }
            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
        }
    }

    @DisplayName("결제 실패 처리 시, ")
    @Nested
    inner class MarkFailed {
        @DisplayName("PENDING 상태의 결제를 FAILED로 변경한다.")
        @Test
        fun markFailed_whenPending() {
            // arrange
            val payment = paymentRepository.save(newPendingPayment())

            // act
            val result = paymentApplicationService.markFailed(
                payment.transactionKey!!,
                "잔액 부족",
            )

            // assert
            assertThat(result.status).isEqualTo(PaymentStatus.FAILED)
            assertThat(result.reason).isEqualTo("잔액 부족")
        }

        @DisplayName("이미 SUCCESS 상태인 결제에 실패 처리하면 CONFLICT 예외가 발생한다.")
        @Test
        fun throwsConflict_whenAlreadySuccess() {
            // arrange
            val payment = paymentRepository.save(newPendingPayment())
            paymentApplicationService.markSuccess(payment.transactionKey!!, "정상 승인되었습니다.")

            // act & assert
            val result = assertThrows<CoreException> {
                paymentApplicationService.markFailed(payment.transactionKey!!, "잔액 부족")
            }
            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
        }
    }

    private fun newPendingPayment(
        orderId: Long = 1L,
        userId: Long = 1L,
        transactionKey: String = "20250623:TR:abc123",
        cardType: String = "SAMSUNG",
        cardNo: String = "1234-5678-9012-3456",
        amount: Long = 10_000L,
    ) = Payment(
        orderId = orderId,
        userId = userId,
        transactionKey = transactionKey,
        cardType = cardType,
        cardNo = cardNo,
        amount = amount,
        status = PaymentStatus.PENDING,
    )
}
