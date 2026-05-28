package com.loopers.infrastructure.payment

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class StubPaymentGatewayTest {
    private val sut = StubPaymentGateway()

    @Test
    @DisplayName("requestPayment 는 항상 success=true, message=null 인 PaymentResult 를 반환한다")
    fun returnsSuccessAlways() {
        val result = sut.requestPayment(orderId = 42L, amount = 10_000L)

        assertThat(result.success).isTrue()
        assertThat(result.message).isNull()
    }

    @Test
    @DisplayName("amount 가 0 이거나 음수여도 동일하게 성공을 반환한다 (Stub 동작)")
    fun returnsSuccessRegardlessOfAmount() {
        val zero = sut.requestPayment(orderId = 1L, amount = 0L)
        val negative = sut.requestPayment(orderId = 1L, amount = -1L)

        assertThat(zero.success).isTrue()
        assertThat(negative.success).isTrue()
    }
}
