package com.loopers.account.domain.vo

import com.loopers.account.domain.CredentialMethod
import com.loopers.account.domain.error.AccountErrorCode
import com.loopers.support.error.BadRequestException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class CredentialIdentifierTest {
    @DisplayName("PASSWORD 인증 방식의 식별자가 영문과 숫자로만 이루어지면, 식별자 VO를 생성한다.")
    @Test
    fun createsCredentialIdentifier_whenPasswordIdentifierIsAlphanumeric() {
        // given
        val value = "shoeone96"

        // when
        val identifier = CredentialIdentifier(CredentialMethod.PASSWORD, value)

        // then
        assertThat(identifier.value).isEqualTo(value)
    }

    @DisplayName("PASSWORD 인증 방식의 식별자가 영문과 숫자 외 문자를 포함하면, BAD_REQUEST 예외가 발생한다.")
    @ParameterizedTest
    @ValueSource(strings = ["", "   ", "shoe one", "사용자", "user!"])
    fun throwsBadRequestException_whenPasswordIdentifierIsNotAlphanumeric(value: String) {
        // when
        val result = assertThrows<BadRequestException> {
            CredentialIdentifier(CredentialMethod.PASSWORD, value)
        }

        // then
        assertAll(
            { assertThat(result.errorCode).isEqualTo(AccountErrorCode.INVALID_CREDENTIAL_IDENTIFIER) },
        )
    }

    @DisplayName("식별자가 255자를 초과하면, BAD_REQUEST 예외가 발생한다.")
    @Test
    fun throwsBadRequestException_whenValueExceedsColumnLength() {
        // given
        val value = "a".repeat(256)

        // when
        val result = assertThrows<BadRequestException> {
            CredentialIdentifier(CredentialMethod.PASSWORD, value)
        }

        // then
        assertThat(result.errorCode).isEqualTo(AccountErrorCode.INVALID_CREDENTIAL_IDENTIFIER)
    }
}
