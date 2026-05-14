package com.loopers.domain.account.vo

import com.loopers.domain.account.CredentialMethod
import com.loopers.support.error.AccountErrorCode
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
        // arrange
        val value = "shoeone96"

        // act
        val identifier = CredentialIdentifier(CredentialMethod.PASSWORD, value)

        // assert
        assertThat(identifier.value).isEqualTo(value)
    }

    @DisplayName("PASSWORD 인증 방식의 식별자가 영문과 숫자 외 문자를 포함하면, BAD_REQUEST 예외가 발생한다.")
    @ParameterizedTest
    @ValueSource(strings = ["", "   ", "shoe one", "사용자", "user!"])
    fun throwsBadRequestException_whenPasswordIdentifierIsNotAlphanumeric(value: String) {
        // act
        val result = assertThrows<BadRequestException> {
            CredentialIdentifier(CredentialMethod.PASSWORD, value)
        }

        // assert
        assertAll(
            { assertThat(result.errorCode).isEqualTo(AccountErrorCode.INVALID_CREDENTIAL_IDENTIFIER) },
        )
    }
}
