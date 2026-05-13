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

class UserTest {
    companion object {
        private const val LOGIN_ID = "user01"
        private const val PASSWORD = "Password1!"
        private const val NAME = "홍길동"
        private const val BIRTH_DATE = "19900628"
        private const val EMAIL = "test@test.com"
    }

    @DisplayName("User 생성 시,")
    @Nested
    inner class Create {
        @DisplayName("유효한 정보가 주어지면, 정상적으로 생성된다.")
        @Test
        fun createsUser_whenValidInfoIsProvided() {
            // act
            val user = User(loginId = LOGIN_ID, password = PASSWORD, name = NAME, birthDate = BIRTH_DATE, email = EMAIL)

            // assert
            assertThat(user.loginId).isEqualTo(LOGIN_ID)
            assertThat(user.name).isEqualTo(NAME)
            assertThat(user.birthDate).isEqualTo(BIRTH_DATE)
            assertThat(user.email).isEqualTo(EMAIL)
        }
    }

    @DisplayName("LoginId 파라미터 유효성 체크")
    @Nested
    inner class InvalidLoginId {
        @DisplayName("로그인 ID가 빈값이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenLoginIdIsBlank() {
            // arrange
            val invalidLoginId = ""

            // act
            val result = assertThrows<CoreException> {
                User(loginId = invalidLoginId, password = PASSWORD, name = NAME, birthDate = BIRTH_DATE, email = EMAIL)
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("로그인 ID에 영문, 숫자 외 문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["user@1", "user 1", "유저01", "user!1"])
        fun throwsBadRequest_whenLoginIdContainsInvalidChar(invalidLoginId: String) {
            // act
            val result = assertThrows<CoreException> {
                User(loginId = invalidLoginId, password = PASSWORD, name = NAME, birthDate = BIRTH_DATE, email = EMAIL)
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("Name 파라미터 유효성 체크")
    @Nested
    inner class InvalidName {
        @DisplayName("이름이 빈값이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenNameIsBlank() {
            // arrange
            val invalidName = ""

            // act
            val result = assertThrows<CoreException> {
                User(loginId = LOGIN_ID, password = PASSWORD, name = invalidName, birthDate = BIRTH_DATE, email = EMAIL)
            }

            // assert
            assertThat(result.message).isEqualTo("이름은 비어있을 수 없습니다.")
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("이름이 2자 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenNameIsTooShort() {
            // arrange
            val invalidName = "홍"

            // act
            val result = assertThrows<CoreException> {
                User(loginId = LOGIN_ID, password = PASSWORD, name = invalidName, birthDate = BIRTH_DATE, email = EMAIL)
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("이름이 20자 초과이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenNameIsTooLong() {
            // arrange
            val invalidName = "홍".repeat(21)

            // act
            val result = assertThrows<CoreException> {
                User(loginId = LOGIN_ID, password = PASSWORD, name = invalidName, birthDate = BIRTH_DATE, email = EMAIL)
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("이름에 숫자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenNameContainsNumber() {
            // arrange
            val invalidName = "만식90"

            // act
            val result = assertThrows<CoreException> {
                User(loginId = LOGIN_ID, password = PASSWORD, name = invalidName, birthDate = BIRTH_DATE, email = EMAIL)
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("이름에 특수문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenNameContainsSpecialChar() {
            // arrange
            val invalidName = "만식@"

            // act
            val result = assertThrows<CoreException> {
                User(loginId = LOGIN_ID, password = PASSWORD, name = invalidName, birthDate = BIRTH_DATE, email = EMAIL)
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("BirthDate 파라미터 유효성 체크")
    @Nested
    inner class InvalidBirthDate {
        @DisplayName("생년월일이 빈값이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenBirthDateIsBlank() {
            // arrange
            val invalidBirthDate = ""

            // act
            val result = assertThrows<CoreException> {
                User(loginId = LOGIN_ID, password = PASSWORD, name = NAME, birthDate = invalidBirthDate, email = EMAIL)
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("생년월일이 yyyyMMdd 형식이 아니면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["1990-06-28", "900628", "19906281", "abcdefgh"])
        fun throwsBadRequest_whenBirthDateIsInvalidFormat(invalidBirthDate: String) {
            // act
            val result = assertThrows<CoreException> {
                User(loginId = LOGIN_ID, password = PASSWORD, name = NAME, birthDate = invalidBirthDate, email = EMAIL)
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("이름 마스킹 시,")
    @Nested
    inner class MaskedName {
        @DisplayName("이름의 마지막 글자가 * 로 마스킹되어 반환된다.")
        @Test
        fun returnsMaskedName_whenCalled() {
            // arrange
            val user = User(loginId = LOGIN_ID, password = PASSWORD, name = "홍길동", birthDate = BIRTH_DATE, email = EMAIL)

            // act
            val result = user.maskedName()

            // assert
            assertThat(result).isEqualTo("홍길*")
        }

        @DisplayName("두 글자 이름도 마지막 글자가 * 로 마스킹되어 반환된다.")
        @Test
        fun returnsMaskedName_whenNameHasTwoChars() {
            // arrange
            val user = User(loginId = LOGIN_ID, password = PASSWORD, name = "홍길", birthDate = BIRTH_DATE, email = EMAIL)

            // act
            val result = user.maskedName()

            // assert
            assertThat(result).isEqualTo("홍*")
        }
    }

    @DisplayName("Email 파라미터 유효성 체크")
    @Nested
    inner class InvalidEmail {
        @DisplayName("이메일이 빈값이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenEmailIsBlank() {
            // arrange
            val invalidEmail = ""

            // act
            val result = assertThrows<CoreException> {
                User(loginId = LOGIN_ID, password = PASSWORD, name = NAME, birthDate = BIRTH_DATE, email = invalidEmail)
            }

            // assert
            assertThat(result.message).isEqualTo("이메일은 필수입니다.")
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("이메일 형식이 올바르지 않으면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["test", "test@", "@test.com", "test@test"])
        fun throwsBadRequest_whenEmailIsInvalidFormat(invalidEmail: String) {
            // act
            val result = assertThrows<CoreException> {
                User(loginId = LOGIN_ID, password = PASSWORD, name = NAME, birthDate = BIRTH_DATE, email = invalidEmail)
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}
