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
                encodedPassword = EncodedPassword(validPassword),
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

    @DisplayName("회원가입 시 ID가, ")
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

    @DisplayName("회원가입 시 이름이, ")
    @Nested
    inner class NameValidation {
        @DisplayName("빈 문자열이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenNameIsEmpty() {
            val result = assertThrows<CoreException> { newUserWith(name = "") }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("20자를 초과하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenNameExceedsMax() {
            val result = assertThrows<CoreException> { newUserWith(name = "가".repeat(21)) }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("회원가입 시 생년월일이, ")
    @Nested
    inner class BirthDateValidation {
        @DisplayName("1900-01-01 이전이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenBirthDateIsBefore1900() {
            val result = assertThrows<CoreException> {
                newUserWith(birthDate = LocalDate.of(1899, 12, 31))
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("가입 날짜 이후이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenBirthDateIsFuture() {
            val result = assertThrows<CoreException> {
                newUserWith(birthDate = LocalDate.now().plusDays(1))
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("가입 날짜와 같으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenBirthDateIsToday() {
            val result = assertThrows<CoreException> {
                newUserWith(birthDate = LocalDate.now())
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("1900-01-01 은 허용된다.")
        @Test
        fun acceptsExact1900_01_01() {
            val user = newUserWith(birthDate = LocalDate.of(1900, 1, 1))
            assertThat(user.birthDate).isEqualTo(LocalDate.of(1900, 1, 1))
        }
    }

    @DisplayName("회원가입 시 이메일이, ")
    @Nested
    inner class EmailValidation {
        @DisplayName("@ 앞의 사용자 식별자가 없으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenEmailHasNoLocalPart() {
            val result = assertThrows<CoreException> { newUserWith(email = "@example.com") }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("@ 가 없으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenEmailHasNoAtSign() {
            val result = assertThrows<CoreException> { newUserWith(email = "invalid-email") }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("도메인이 없으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenEmailHasNoDomain() {
            val result = assertThrows<CoreException> { newUserWith(email = "user@") }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("TLD 가 없으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenEmailHasNoTld() {
            val result = assertThrows<CoreException> { newUserWith(email = "user@example") }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("비밀번호 수정 시, ")
    @Nested
    inner class ChangePassword {
        @DisplayName("유효한 암호화 비밀번호로 변경하면 password 가 갱신된다.")
        @Test
        fun changesPassword_whenEncodedPasswordIsValid() {
            // arrange
            val user = newUserWith()
            val newEncodedPassword = EncodedPassword("newEncodedValue")

            // act
            user.changePassword(newEncodedPassword)

            // assert
            assertThat(user.password).isEqualTo("newEncodedValue")
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
        encodedPassword = EncodedPassword(password),
        name = name,
        birthDate = birthDate,
        email = email,
    )
}
