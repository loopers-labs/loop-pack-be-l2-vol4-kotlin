package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class UserModelTest {
    private val validLoginId = "testUser01"
    private val validEncodedPw = "\$2a\$10\$abcdefghijklmnopqrstuuVGmOEMWxQJNz0lkZ.abcdefghijklmno"
    private val validName = "홍길동"
    private val validBirthDate = "19900101"
    private val validEmail = "test@example.com"

    @DisplayName("유저 생성 시,")
    @Nested
    inner class Create {
        @DisplayName("유효한 정보가 모두 주어지면, 정상적으로 생성된다.")
        @Test
        fun createsUserModel_whenAllValidInfoIsProvided() {
            // arrange & act
            val user = UserModel(
                loginId = validLoginId,
                encodedPassword = validEncodedPw,
                name = validName,
                birthDate = validBirthDate,
                email = validEmail,
            )

            // assert
            assertAll(
                { assertThat(user.loginId).isEqualTo(validLoginId) },
                { assertThat(user.name).isEqualTo(validName) },
                { assertThat(user.birthDate).isEqualTo(validBirthDate) },
                { assertThat(user.email).isEqualTo(validEmail) },
            )
        }

        @DisplayName("로그인 ID가 공백이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenLoginIdIsBlank() {
            // arrange & act
            val exception = assertThrows<CoreException> {
                UserModel(loginId = "   ", encodedPassword = validEncodedPw, name = validName, birthDate = validBirthDate, email = validEmail)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("로그인 ID가 4자 미만이거나 영문/숫자 외 문자를 포함하면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["ab", "abc", "로그인ID", "login-id", "log in"])
        fun throwsBadRequest_whenLoginIdIsInvalidFormat(loginId: String) {
            // arrange & act
            val exception = assertThrows<CoreException> {
                UserModel(loginId = loginId, encodedPassword = validEncodedPw, name = validName, birthDate = validBirthDate, email = validEmail)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("로그인 ID가 20자를 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenLoginIdExceedsMaxLength() {
            // arrange
            val tooLong = "a".repeat(21)

            // act
            val exception = assertThrows<CoreException> {
                UserModel(loginId = tooLong, encodedPassword = validEncodedPw, name = validName, birthDate = validBirthDate, email = validEmail)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("이름이 공백이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenNameIsBlank() {
            // arrange & act
            val exception = assertThrows<CoreException> {
                UserModel(loginId = validLoginId, encodedPassword = validEncodedPw, name = "  ", birthDate = validBirthDate, email = validEmail)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("이름에 숫자 또는 특수문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["홍길동1", "hong123", "hong!dong", "홍!동"])
        fun throwsBadRequest_whenNameContainsInvalidCharacters(name: String) {
            // arrange & act
            val exception = assertThrows<CoreException> {
                UserModel(loginId = validLoginId, encodedPassword = validEncodedPw, name = name, birthDate = validBirthDate, email = validEmail)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("생년월일이 공백이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenBirthDateIsBlank() {
            // arrange & act
            val exception = assertThrows<CoreException> {
                UserModel(loginId = validLoginId, encodedPassword = validEncodedPw, name = validName, birthDate = "  ", email = validEmail)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("생년월일이 yyyyMMdd 형식이 아니거나 존재하지 않는 날짜이면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["1990-01-01", "900101", "abcdefgh", "19901301", "19900132"])
        fun throwsBadRequest_whenBirthDateIsInvalid(birthDate: String) {
            // arrange & act
            val exception = assertThrows<CoreException> {
                UserModel(loginId = validLoginId, encodedPassword = validEncodedPw, name = validName, birthDate = birthDate, email = validEmail)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("이메일이 공백이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenEmailIsBlank() {
            // arrange & act
            val exception = assertThrows<CoreException> {
                UserModel(loginId = validLoginId, encodedPassword = validEncodedPw, name = validName, birthDate = validBirthDate, email = "  ")
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("이메일 형식이 올바르지 않으면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["notanemail", "missing@domain", "@nodomain.com", "double@@test.com"])
        fun throwsBadRequest_whenEmailIsInvalidFormat(email: String) {
            // arrange & act
            val exception = assertThrows<CoreException> {
                UserModel(loginId = validLoginId, encodedPassword = validEncodedPw, name = validName, birthDate = validBirthDate, email = email)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("이름 마스킹 시,")
    @Nested
    inner class MaskedName {
        @DisplayName("이름이 1글자이면 * 만 반환한다.")
        @Test
        fun returnsSingleAsterisk_whenNameHasOneCharacter() {
            // arrange
            val user = UserModel(loginId = validLoginId, encodedPassword = validEncodedPw, name = "홍", birthDate = validBirthDate, email = validEmail)

            // act
            val result = user.maskedName()

            // assert
            assertThat(result).isEqualTo("*")
        }

        @DisplayName("이름이 여러 글자이면 마지막 글자만 * 로 마스킹한다.")
        @Test
        fun returnsNameWithLastCharMasked_whenNameHasMultipleCharacters() {
            // arrange
            val user = UserModel(loginId = validLoginId, encodedPassword = validEncodedPw, name = "홍길동", birthDate = validBirthDate, email = validEmail)

            // act
            val result = user.maskedName()

            // assert
            assertThat(result).isEqualTo("홍길*")
        }
    }

    @DisplayName("비밀번호 형식 검증 시,")
    @Nested
    inner class ValidateRawPassword {
        @DisplayName("유효한 비밀번호이면 예외가 발생하지 않는다.")
        @Test
        fun doesNotThrow_whenPasswordIsValid() {
            // arrange & act & assert
            UserModel.validateRawPassword("Pass!234", validBirthDate)
        }

        @DisplayName("비밀번호가 8자 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenPasswordIsTooShort() {
            // arrange & act
            val exception = assertThrows<CoreException> {
                UserModel.validateRawPassword("Pass!23", validBirthDate)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("비밀번호가 16자를 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenPasswordExceedsMaxLength() {
            // arrange & act
            val exception = assertThrows<CoreException> {
                UserModel.validateRawPassword("Pass!23456789012345", validBirthDate)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("비밀번호에 허용되지 않은 문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["Pass 234", "Pass\t234", "Pass\n234"])
        fun throwsBadRequest_whenPasswordContainsInvalidCharacters(password: String) {
            // arrange & act
            val exception = assertThrows<CoreException> {
                UserModel.validateRawPassword(password, validBirthDate)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("비밀번호에 생년월일이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenPasswordContainsBirthDate() {
            // arrange & act
            val exception = assertThrows<CoreException> {
                UserModel.validateRawPassword("19900101Pw!", validBirthDate)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("비밀번호 변경 시,")
    @Nested
    inner class ChangePassword {
        @DisplayName("새 인코딩된 비밀번호를 전달하면, 비밀번호가 업데이트된다.")
        @Test
        fun updatesEncodedPassword_whenNewEncodedPasswordIsProvided() {
            // arrange
            val user = UserModel(
                loginId = validLoginId,
                encodedPassword = validEncodedPw,
                name = validName,
                birthDate = validBirthDate,
                email = validEmail,
            )
            val newEncodedPw = "\$2a\$10\$zyxwvutsrqponmlkjihgfuuVGmOEMWxQJNz0lkZ.abcdefghijklmno"

            // act
            user.changePassword(newEncodedPw)

            // assert
            assertThat(user.encodedPassword).isEqualTo(newEncodedPw)
        }
    }
}
