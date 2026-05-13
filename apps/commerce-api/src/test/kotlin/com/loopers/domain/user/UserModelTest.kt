package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class UserModelTest {
    @DisplayName("회원 가입 시, ")
    @Nested
    inner class CreateUser {
        @DisplayName("모든 값이 유효하면 정상적으로 가입된다.")
        @Test
        fun createUser_whenAllFieldsAreValid() {
            // arrange
            val validLoginId = "seondays"
            val validPassword = "password"
            val validName = "선데이"
            val validBirthDate = LocalDate.of(1990, 1, 1)
            val validEmail = "seondays@example.com"

            // act
            val user = UserModel(
                loginId = validLoginId,
                password = validPassword,
                name = validName,
                birthDate = validBirthDate,
                email = validEmail,
            )

            // assert
            assertAll(
                { assertThat(user.loginId).isEqualTo(validLoginId) },
                { assertThat(user.password).isEqualTo(validPassword) },
                { assertThat(user.name).isEqualTo(validName) },
                { assertThat(user.birthDate).isEqualTo(validBirthDate) },
                { assertThat(user.email).isEqualTo(validEmail) },
            )
        }
    }

    @DisplayName("회원가입 시 ID 검증에서, ")
    @Nested
    inner class LoginIdValidation {
        @DisplayName("3자 미만이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenLoginIdIsTooShort() {
            val result = assertThrows<CoreException> { newUserWith(loginId = "ab") }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("20자를 초과하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenLoginIdExceedsMax() {
            val result = assertThrows<CoreException> { newUserWith(loginId = "a".repeat(21)) }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("대문자가 포함되면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenLoginIdContainsUppercase() {
            val result = assertThrows<CoreException> { newUserWith(loginId = "Loopers01") }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("한글이 포함되면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenLoginIdContainsKorean() {
            val result = assertThrows<CoreException> { newUserWith(loginId = "루퍼스01") }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("특수문자가 포함되면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenLoginIdContainsSpecialChar() {
            val result = assertThrows<CoreException> { newUserWith(loginId = "loopers!") }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    private fun newUserWith(
        loginId: String = "seondays",
        password: String = "password",
        name: String = "선데이",
        birthDate: LocalDate = LocalDate.of(1990, 1, 1),
        email: String = "seondays@example.com",
    ) = UserModel(
        loginId = loginId,
        password = password,
        name = name,
        birthDate = birthDate,
        email = email,
    )
}
