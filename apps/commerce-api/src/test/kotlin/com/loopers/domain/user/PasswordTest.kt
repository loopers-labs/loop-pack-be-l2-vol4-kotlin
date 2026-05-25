package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class PasswordTest {
    private val defaultBirth = LocalDate.of(2000, 1, 1)

    @DisplayName("비밀번호를 생성할 때, ")
    @Nested
    inner class Create {

        @DisplayName("유효한 비밀번호면, 암호화되어 생성된다.")
        @Test
        fun createsPassword_whenValid() {
            // arrange
            val rawPassword = "Password1!"

            // act
            val password = Password.create(rawPassword, defaultBirth)

            // assert
            assertThat(password.value).isNotEqualTo(rawPassword)
            assertThat(password.value).isEqualTo(PasswordEncryptionUtil.encode(rawPassword))
        }

        @DisplayName("8자 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenTooShort() {
            // arrange
            val rawPassword = "Pass12!"

            // act
            val result = assertThrows<CoreException> {
                Password.create(rawPassword, defaultBirth)
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("16자를 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenTooLong() {
            // arrange
            val rawPassword = "Password1!abcdefg"

            // act
            val result = assertThrows<CoreException> {
                Password.create(rawPassword, defaultBirth)
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("정확히 8자이면, 정상적으로 생성된다.")
        @Test
        fun createsPassword_whenExactly8Characters() {
            // arrange
            val rawPassword = "Passwo1!"

            // act
            val password = Password.create(rawPassword, defaultBirth)

            // assert
            assertThat(password.value).isEqualTo(PasswordEncryptionUtil.encode(rawPassword))
        }

        @DisplayName("정확히 16자이면, 정상적으로 생성된다.")
        @Test
        fun createsPassword_whenExactly16Characters() {
            // arrange
            val rawPassword = "Passwo1!Passwo1!"

            // act
            val password = Password.create(rawPassword, defaultBirth)

            // assert
            assertThat(password.value).isEqualTo(PasswordEncryptionUtil.encode(rawPassword))
        }

        @DisplayName("영어 대소문자, 숫자, 특수문자를 제외한 문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenContainsKorean() {
            // arrange
            val rawPassword = "Password1한글"

            // act
            val result = assertThrows<CoreException> {
                Password.create(rawPassword, defaultBirth)
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("공백이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenContainsSpace() {
            // arrange
            val rawPassword = "Pass word1!"

            // act
            val result = assertThrows<CoreException> {
                Password.create(rawPassword, defaultBirth)
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("생년월일(yyyyMMdd)이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenContainsBirthday() {
            // arrange
            val rawPassword = "a20000101!"

            // act
            val result = assertThrows<CoreException> {
                Password.create(rawPassword, defaultBirth)
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("비밀번호를 비교할 때, ")
    @Nested
    inner class Matches {

        @DisplayName("원문 비밀번호와 일치하면, true를 반환한다.")
        @Test
        fun returnsTrue_whenMatches() {
            // arrange
            val rawPassword = "Password1!"
            val password = Password.create(rawPassword, defaultBirth)

            // act
            val result = password.matches(rawPassword)

            // assert
            assertThat(result).isTrue()
        }

        @DisplayName("원문 비밀번호와 다르면, false를 반환한다.")
        @Test
        fun returnsFalse_whenDoesNotMatch() {
            // arrange
            val rawPassword = "Password1!"
            val password = Password.create(rawPassword, defaultBirth)

            // act
            val result = password.matches("WrongPassword1!")

            // assert
            assertThat(result).isFalse()
        }
    }
}
