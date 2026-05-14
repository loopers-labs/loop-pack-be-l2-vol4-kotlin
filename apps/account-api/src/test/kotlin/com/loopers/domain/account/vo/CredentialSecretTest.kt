package com.loopers.domain.account.vo

import com.loopers.support.error.AccountErrorCode
import com.loopers.support.error.BadRequestException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

class CredentialSecretTest {
    @DisplayName("암호화된 비밀번호가 주어지면, credential secret VO를 생성한다.")
    @Test
    fun createsCredentialSecret_whenValueIsNotBlank() {
        // arrange
        val value = "{bcrypt}encrypted-password"

        // act
        val secret = CredentialSecret(value)

        // assert
        assertThat(secret.value).isEqualTo(value)
    }

    @DisplayName("암호화된 비밀번호가 비어있으면, BAD_REQUEST 예외가 발생한다.")
    @Test
    fun throwsBadRequestException_whenValueIsBlank() {
        // arrange
        val value = "   "

        // act
        val result = assertThrows<BadRequestException> {
            CredentialSecret(value)
        }

        // assert
        assertAll(
            { assertThat(result.errorCode).isEqualTo(AccountErrorCode.INVALID_CREDENTIAL_SECRET) },
        )
    }
}
