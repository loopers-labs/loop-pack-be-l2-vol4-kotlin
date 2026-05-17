package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

class PasswordTest {

    companion object {
        private const val BIRTH_DATE = "19900628"
        private const val VALID_PASSWORD = "Password1!"
    }

    @DisplayName("Password 유효성 체크")
    @Nested
    inner class Invalid {
        @DisplayName("비밀번호가 빈값이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenPasswordIsBlank() {
            // arrange
            val invalidPassword = ""

            // act
            val result = assertThrows<CoreException> {
                Password(invalidPassword, BIRTH_DATE)
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("비밀번호가 8자 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenPasswordIsTooShort() {
            // arrange
            val invalidPassword = "Pass1!"

            // act
            val result = assertThrows<CoreException> {
                Password(invalidPassword, BIRTH_DATE)
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("비밀번호가 16자 초과이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenPasswordIsTooLong() {
            // arrange
            val invalidPassword = "Password123456789!"

            // act
            val result = assertThrows<CoreException> {
                Password(invalidPassword, BIRTH_DATE)
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("비밀번호에 허용되지 않는 문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["Pass word1!", "비밀번호1234!"])
        fun throwsBadRequest_whenPasswordContainsInvalidChar(invalidPassword: String) {
            // act
            val result = assertThrows<CoreException> {
                Password(invalidPassword, BIRTH_DATE)
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("비밀번호에 생년월일이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenPasswordContainsBirthDate() {
            // arrange
            val invalidPassword = "19900628A!"

            // act
            val result = assertThrows<CoreException> {
                Password(invalidPassword, BIRTH_DATE)
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("encode 시,")
    @Nested
    inner class Encode {
        @DisplayName("PasswordEncryptor로 암호화하면, 평문과 다른 값이 반환된다.")
        @Test
        fun encode_returnsDifferentValueFromRaw() {
            // arrange
            val encryptor = mock<PasswordEncryptor>()
            whenever(encryptor.encode(VALID_PASSWORD)).thenReturn("\$2a\$10\$hashedvalue")
            val password = Password(VALID_PASSWORD, BIRTH_DATE)

            // act
            val encoded = password.encode(encryptor)

            // assert
            assertThat(encoded).isNotEqualTo(VALID_PASSWORD)
            assertThat(encoded).isEqualTo("\$2a\$10\$hashedvalue")
        }
    }
}
