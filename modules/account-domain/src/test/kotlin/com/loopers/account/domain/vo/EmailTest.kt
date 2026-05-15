package com.loopers.account.domain.vo

import com.loopers.account.domain.error.AccountErrorCode
import com.loopers.support.error.BadRequestException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class EmailTest {
    @DisplayName("정상 이메일이 주어지면, 이메일 VO를 생성한다.")
    @Test
    fun createsEmail_whenValueMatchesEmailPattern() {
        // given
        val value = "user@example.com"

        // when
        val email = Email(value)

        // then
        assertThat(email.value).isEqualTo(value)
    }

    @DisplayName("이메일 형식이 아니면, BAD_REQUEST 예외가 발생한다.")
    @ParameterizedTest
    @ValueSource(strings = ["", "   ", "user", "@example.com", "user@"])
    fun throwsBadRequestException_whenValueDoesNotMatchEmailPattern(value: String) {
        // when
        val result = assertThrows<BadRequestException> {
            Email(value)
        }

        // then
        assertAll(
            { assertThat(result.errorCode).isEqualTo(AccountErrorCode.INVALID_EMAIL) },
        )
    }
}
