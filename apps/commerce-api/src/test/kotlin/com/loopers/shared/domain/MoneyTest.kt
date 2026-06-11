package com.loopers.shared.domain

import com.loopers.support.error.BadRequestException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

class MoneyTest {
    @DisplayName("0원이 주어지면, 금액 VO를 생성한다.")
    @Test
    fun createsMoney_whenAmountIsZero() {
        val money = Money(0)
        assertThat(money.amount).isEqualTo(0)
    }

    @DisplayName("양수 금액이 주어지면, 금액 VO를 생성한다.")
    @Test
    fun createsMoney_whenAmountIsPositive() {
        val money = Money(10_000)
        assertThat(money.amount).isEqualTo(10_000)
    }

    @DisplayName("음수 금액이 주어지면, BAD_REQUEST 예외가 발생한다.")
    @Test
    fun throwsBadRequestException_whenAmountIsNegative() {
        val result = assertThrows<BadRequestException> { Money(-1) }
        assertThat(result.errorCode).isEqualTo(MoneyErrorCode.INVALID_MONEY)
    }

    @DisplayName("같은 금액의 VO는 동등하다.")
    @Test
    fun equalsAndHashCode_followValueSemantics() {
        val first = Money(5_000)
        val second = Money(5_000)
        assertAll(
            { assertThat(first).isEqualTo(second) },
            { assertThat(first.hashCode()).isEqualTo(second.hashCode()) },
        )
    }
}
