package com.loopers.domain.payment

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PaymentTest {

    private fun pending(): Payment = Payment.create(
        userId = 1L,
        orderId = 10L,
        transactionKey = "TX-0001",
        cardType = "SAMSUNG",
        cardNo = "1234-5678-9814-1451",
        amount = 5_000L,
    )

    @DisplayName("create")
    @Nested
    inner class Create {
        @DisplayName("생성된 결제는 PENDING 상태이다.")
        @Test
        fun startsAsPending() {
            val payment = pending()

            assertThat(payment.status).isEqualTo(PaymentStatus.PENDING)
            assertThat(payment.reason).isNull()
        }

        @DisplayName("결제 금액이 0 이하이면 생성할 수 없다.")
        @Test
        fun rejectsNonPositiveAmount() {
            assertThrows<IllegalArgumentException> {
                Payment.create(
                    userId = 1L,
                    orderId = 10L,
                    transactionKey = "TX-0001",
                    cardType = "KB",
                    cardNo = "1234-5678-9814-1451",
                    amount = 0L,
                )
            }
        }

        @DisplayName("트랜잭션 키가 비어 있으면 생성할 수 없다.")
        @Test
        fun rejectsBlankTransactionKey() {
            assertThrows<IllegalArgumentException> {
                Payment.create(
                    userId = 1L,
                    orderId = 10L,
                    transactionKey = " ",
                    cardType = "HYUNDAI",
                    cardNo = "1234-5678-9814-1451",
                    amount = 5_000L,
                )
            }
        }
    }

    @DisplayName("approve")
    @Nested
    inner class Approve {
        @DisplayName("PENDING 결제를 승인하면 SUCCESS 가 되고 사유가 채워진다.")
        @Test
        fun approvesPendingPayment() {
            val approved = pending().approve()

            assertThat(approved.status).isEqualTo(PaymentStatus.SUCCESS)
            assertThat(approved.reason).isNotBlank()
        }

        @DisplayName("이미 승인된 결제를 다시 승인하면 예외가 발생한다.")
        @Test
        fun rejectsApproveWhenNotPending() {
            val approved = pending().approve()

            assertThrows<IllegalArgumentException> { approved.approve() }
        }
    }

    @DisplayName("fail")
    @Nested
    inner class Fail {
        @DisplayName("PENDING 결제를 실패 처리하면 FAILED 가 되고 사유가 채워진다.")
        @Test
        fun failsPendingPayment() {
            val failed = pending().fail("한도초과")

            assertThat(failed.status).isEqualTo(PaymentStatus.FAILED)
            assertThat(failed.reason).isEqualTo("한도초과")
        }

        @DisplayName("사유가 없으면 기본 실패 사유로 채워진다.")
        @Test
        fun fillsDefaultReason_whenNull() {
            val failed = pending().fail(null)

            assertThat(failed.status).isEqualTo(PaymentStatus.FAILED)
            assertThat(failed.reason).isNotBlank()
        }

        @DisplayName("이미 종료된 결제를 실패 처리하면 예외가 발생한다.")
        @Test
        fun rejectsFailWhenNotPending() {
            val approved = pending().approve()

            assertThrows<IllegalArgumentException> { approved.fail("늦은 실패") }
        }
    }
}
