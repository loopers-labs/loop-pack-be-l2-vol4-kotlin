package com.loopers.domain.payment

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

class PaymentModelTest {
    private fun payment(
        orderId: Long = 1L,
        userId: Long = 1L,
        cardType: CardType = CardType.SAMSUNG,
        cardNo: String = "1234-5678-9814-1451",
        amount: Long = 5_000,
    ): PaymentModel = PaymentModel.create(
        orderId = orderId,
        userId = userId,
        cardType = cardType,
        cardNo = cardNo,
        amount = amount,
    )

    @DisplayName("결제를 생성할 때,")
    @Nested
    inner class Create {
        @DisplayName("유효한 값으로 생성하면, 상태는 PENDING이고 transactionKey는 비어있다.")
        @Test
        fun createsAsPending() {
            // act
            val payment = payment(orderId = 10L, amount = 5_000)

            // assert
            assertAll(
                { assertThat(payment.orderId).isEqualTo(10L) },
                { assertThat(payment.amount).isEqualTo(5_000L) },
                { assertThat(payment.status).isEqualTo(PaymentStatus.PENDING) },
                { assertThat(payment.transactionKey).isNull() },
                { assertThat(payment.reason).isNull() },
            )
        }

        @DisplayName("결제 금액이 0 이하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenAmountIsZeroOrLess() {
            val result = assertThrows<CoreException> { payment(amount = 0) }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("카드 번호 형식이 올바르지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenCardNoFormatIsInvalid() {
            val result = assertThrows<CoreException> { payment(cardNo = "1234567898141451") }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("PG 요청을 접수할 때,")
    @Nested
    inner class AssignTransactionKey {
        @DisplayName("transactionKey가 없으면, 발급받은 키를 보관한다.")
        @Test
        fun assignsKey() {
            val payment = payment()
            payment.assignTransactionKey("20250816:TR:9577c5")
            assertThat(payment.transactionKey).isEqualTo("20250816:TR:9577c5")
        }

        @DisplayName("동일한 키로 다시 접수해도, 예외 없이 멱등하게 처리된다.")
        @Test
        fun assignSameKey_isIdempotent() {
            val payment = payment()
            payment.assignTransactionKey("TR-1")
            payment.assignTransactionKey("TR-1")
            assertThat(payment.transactionKey).isEqualTo("TR-1")
        }

        @DisplayName("이미 다른 키가 보관되어 있으면, CONFLICT 예외가 발생한다.")
        @Test
        fun throwsConflict_whenKeyAlreadyAssignedDifferently() {
            val payment = payment()
            payment.assignTransactionKey("TR-1")
            val result = assertThrows<CoreException> { payment.assignTransactionKey("TR-2") }
            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
        }
    }

    @DisplayName("결제 결과를 반영할 때,")
    @Nested
    inner class Settle {
        @DisplayName("markSuccess: PENDING -> SUCCESS로 전이된다.")
        @Test
        fun markSuccess_pendingToSuccess() {
            val payment = payment()
            payment.markSuccess()
            assertThat(payment.status).isEqualTo(PaymentStatus.SUCCESS)
        }

        @DisplayName("markFailed: PENDING -> FAILED로 전이되고 사유가 기록된다.")
        @Test
        fun markFailed_pendingToFailed() {
            val payment = payment()
            payment.markFailed("한도초과")
            assertAll(
                { assertThat(payment.status).isEqualTo(PaymentStatus.FAILED) },
                { assertThat(payment.reason).isEqualTo("한도초과") },
            )
        }

        @DisplayName("markSuccess: 이미 SUCCESS 상태에서 다시 호출해도, 예외 없이 멱등하게 처리된다.")
        @Test
        fun markSuccess_isIdempotent() {
            val payment = payment()
            payment.markSuccess()
            payment.markSuccess()
            assertThat(payment.status).isEqualTo(PaymentStatus.SUCCESS)
        }

        @DisplayName("markFailed: 이미 FAILED 상태에서 다시 호출해도, 예외 없이 멱등하게 처리된다.")
        @Test
        fun markFailed_isIdempotent() {
            val payment = payment()
            payment.markFailed("잘못된 카드")
            payment.markFailed("잘못된 카드")
            assertThat(payment.status).isEqualTo(PaymentStatus.FAILED)
        }

        @DisplayName("markSuccess: 이미 FAILED로 확정된 건을 SUCCESS로 바꾸려 하면, CONFLICT 예외가 발생한다.")
        @Test
        fun markSuccess_throwsConflict_whenAlreadyFailed() {
            val payment = payment()
            payment.markFailed("한도초과")
            val result = assertThrows<CoreException> { payment.markSuccess() }
            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
        }

        @DisplayName("markFailed: 이미 SUCCESS로 확정된 건을 FAILED로 바꾸려 하면, CONFLICT 예외가 발생한다.")
        @Test
        fun markFailed_throwsConflict_whenAlreadySuccess() {
            val payment = payment()
            payment.markSuccess()
            val result = assertThrows<CoreException> { payment.markFailed("한도초과") }
            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
        }
    }

    @DisplayName("재결제를 위해 결제를 재개할 때,")
    @Nested
    inner class Reopen {
        @DisplayName("reopen: FAILED -> PENDING으로 전이되고 transactionKey와 사유가 초기화된다.")
        @Test
        fun reopen_failedToPending() {
            val payment = payment()
            payment.assignTransactionKey("TR-1")
            payment.markFailed("한도초과")

            payment.reopen()

            assertAll(
                { assertThat(payment.status).isEqualTo(PaymentStatus.PENDING) },
                { assertThat(payment.transactionKey).isNull() },
                { assertThat(payment.reason).isNull() },
            )
        }

        @DisplayName("reopen: PENDING 상태에서 호출해도, 예외 없이 멱등하게 처리된다.")
        @Test
        fun reopen_isIdempotent() {
            val payment = payment()
            payment.reopen()
            assertThat(payment.status).isEqualTo(PaymentStatus.PENDING)
        }

        @DisplayName("reopen: 이미 SUCCESS로 확정된 건은 재개할 수 없어, CONFLICT 예외가 발생한다.")
        @Test
        fun reopen_throwsConflict_whenAlreadySuccess() {
            val payment = payment()
            payment.markSuccess()
            val result = assertThrows<CoreException> { payment.reopen() }
            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
        }
    }
}
