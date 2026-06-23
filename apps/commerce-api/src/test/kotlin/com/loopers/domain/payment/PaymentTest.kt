package com.loopers.domain.payment

import com.loopers.application.payment.PaymentStatus
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

class PaymentTest {

    @DisplayName("결제 생성 시, ")
    @Nested
    inner class CreatePayment {
        @DisplayName("유효한 정보로 생성하면 PENDING 상태의 결제가 생성된다.")
        @Test
        fun createPayment_whenAllFieldsAreValid() {
            // act
            val payment = newPayment()

            // assert
            assertAll(
                { assertThat(payment.orderId).isEqualTo(1L) },
                { assertThat(payment.userId).isEqualTo(1L) },
                { assertThat(payment.transactionKey).isEqualTo("20250623:TR:abc123") },
                { assertThat(payment.cardType).isEqualTo("SAMSUNG") },
                { assertThat(payment.cardNo).isEqualTo("1234-5678-9012-3456") },
                { assertThat(payment.amount).isEqualTo(10_000L) },
                { assertThat(payment.status).isEqualTo(PaymentStatus.PENDING) },
                { assertThat(payment.reason).isNull() },
            )
        }
    }

    @DisplayName("결제 상태 변경 시, ")
    @Nested
    inner class ChangeStatus {
        @DisplayName("PENDING 상태에서 SUCCESS로 변경할 수 있다.")
        @Test
        fun markSuccess() {
            // arrange
            val payment = newPayment()

            // act
            payment.markSuccess("정상 승인되었습니다.")

            // assert
            assertAll(
                { assertThat(payment.status).isEqualTo(PaymentStatus.SUCCESS) },
                { assertThat(payment.reason).isEqualTo("정상 승인되었습니다.") },
            )
        }

        @DisplayName("PENDING 상태에서 FAILED로 변경할 수 있다.")
        @Test
        fun markFailed() {
            // arrange
            val payment = newPayment()

            // act
            payment.markFailed("잔액 부족")

            // assert
            assertAll(
                { assertThat(payment.status).isEqualTo(PaymentStatus.FAILED) },
                { assertThat(payment.reason).isEqualTo("잔액 부족") },
            )
        }

        @DisplayName("SUCCESS 상태에서 다시 상태를 변경할 수 없다.")
        @Test
        fun throwsBadRequest_whenAlreadySuccess() {
            // arrange
            val payment = newPayment()
            payment.markSuccess("정상 승인되었습니다.")

            // act & assert
            val result = assertThrows<CoreException> {
                payment.markFailed("잔액 부족")
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("FAILED 상태에서 다시 상태를 변경할 수 없다.")
        @Test
        fun throwsBadRequest_whenAlreadyFailed() {
            // arrange
            val payment = newPayment()
            payment.markFailed("잔액 부족")

            // act & assert
            val result = assertThrows<CoreException> {
                payment.markSuccess("정상 승인되었습니다.")
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    private fun newPayment(
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
    )
}
