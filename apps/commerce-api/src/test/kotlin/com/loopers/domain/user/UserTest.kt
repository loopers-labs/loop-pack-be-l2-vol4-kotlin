package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class UserTest {
    private fun createUser(
        loginId: String = "testuser",
        rawPassword: String = "Password1!",
        name: String = "테스트",
        birth: LocalDate = LocalDate.of(2000, 1, 1),
        email: String = "test@test.com",
    ): User = User.create(
        loginId = loginId,
        rawPassword = rawPassword,
        name = name,
        birth = birth,
        email = email,
    )

    @DisplayName("유저를 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("영문과 숫자로만 이루어진 아이디면, 정상적으로 생성된다.")
        @Test
        fun createsUser_whenLoginIdIsAlphanumeric() {
            // arrange
            val loginId = "testUser123"

            // act
            val user = createUser(loginId = loginId)

            // assert
            assertThat(user.loginId).isEqualTo(loginId)
        }

        @DisplayName("아이디에 한글이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenLoginIdContainsKorean() {
            // arrange
            val loginId = "test유저"

            // act
            val result = assertThrows<CoreException> {
                createUser(loginId = loginId)
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("아이디에 특수문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenLoginIdContainsSpecialCharacters() {
            // arrange
            val loginId = "test@user!"

            // act
            val result = assertThrows<CoreException> {
                createUser(loginId = loginId)
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("아이디에 공백이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenLoginIdContainsSpace() {
            // arrange
            val loginId = "test user"

            // act
            val result = assertThrows<CoreException> {
                createUser(loginId = loginId)
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("아이디가 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenLoginIdIsEmpty() {
            // arrange
            val loginId = ""

            // act
            val result = assertThrows<CoreException> {
                createUser(loginId = loginId)
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("올바른 정보가 주어지면, 정상적으로 생성되고 비밀번호는 암호화된다.")
        @Test
        fun createsUser_whenValidInfoIsProvided() {
            // arrange
            val loginId = "testuser"
            val rawPassword = "Password1!"
            val name = "테스트"
            val birth = LocalDate.of(2000, 1, 1)
            val email = "test@test.com"

            // act
            val user = User.create(
                loginId = loginId,
                rawPassword = rawPassword,
                name = name,
                birth = birth,
                email = email,
            )

            // assert
            val expected = User(
                loginId = loginId,
                password = Password(PasswordEncryptionUtil.encode(rawPassword)),
                name = name,
                birth = birth,
                email = email,
            )
            assertThat(user).isEqualTo(expected)
        }

        @DisplayName("비밀번호가 8자 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenPasswordIsTooShort() {
            // arrange
            val rawPassword = "Pass1!"

            // act
            val result = assertThrows<CoreException> {
                User.create(
                    loginId = "testuser",
                    rawPassword = rawPassword,
                    name = "테스트",
                    birth = LocalDate.of(2000, 1, 1),
                    email = "test@test.com",
                )
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("비밀번호가 16자를 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenPasswordIsTooLong() {
            // arrange
            val rawPassword = "Password1!abcdefg"

            // act
            val result = assertThrows<CoreException> {
                User.create(
                    loginId = "testuser",
                    rawPassword = rawPassword,
                    name = "테스트",
                    birth = LocalDate.of(2000, 1, 1),
                    email = "test@test.com",
                )
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("비밀번호에 허용되지 않은 문자(한글)가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenPasswordContainsKorean() {
            // arrange
            val rawPassword = "Password1한글"

            // act
            val result = assertThrows<CoreException> {
                User.create(
                    loginId = "testuser",
                    rawPassword = rawPassword,
                    name = "테스트",
                    birth = LocalDate.of(2000, 1, 1),
                    email = "test@test.com",
                )
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("비밀번호에 공백이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenPasswordContainsSpace() {
            // arrange
            val rawPassword = "Pass word1!"

            // act
            val result = assertThrows<CoreException> {
                User.create(
                    loginId = "testuser",
                    rawPassword = rawPassword,
                    name = "테스트",
                    birth = LocalDate.of(2000, 1, 1),
                    email = "test@test.com",
                )
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("비밀번호에 생년월일(yyyyMMdd)이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenPasswordContainsBirthday() {
            // arrange
            val birth = LocalDate.of(2000, 1, 1)
            val rawPassword = "a20000101!"

            // act
            val result = assertThrows<CoreException> {
                User.create(
                    loginId = "testuser",
                    rawPassword = rawPassword,
                    name = "테스트",
                    birth = birth,
                    email = "test@test.com",
                )
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("비밀번호가 정확히 8자이면, 정상적으로 생성된다.")
        @Test
        fun createsUser_whenPasswordIsExactly8Characters() {
            // arrange
            val rawPassword = "Passwo1!"

            // act
            val user = User.create(
                loginId = "testuser",
                rawPassword = rawPassword,
                name = "테스트",
                birth = LocalDate.of(2000, 1, 1),
                email = "test@test.com",
            )

            // assert
            assertThat(user.password).isNotEqualTo(rawPassword)
        }

        @DisplayName("비밀번호가 정확히 16자이면, 정상적으로 생성된다.")
        @Test
        fun createsUser_whenPasswordIsExactly16Characters() {
            // arrange
            val rawPassword = "Passwo1!Passwo1!"

            // act
            val user = User.create(
                loginId = "testuser",
                rawPassword = rawPassword,
                name = "테스트",
                birth = LocalDate.of(2000, 1, 1),
                email = "test@test.com",
            )

            // assert
            assertThat(user.password).isNotEqualTo(rawPassword)
        }
    }

    @DisplayName("비밀번호를 확인할 때, ")
    @Nested
    inner class IsCorrectPasswd {

        @DisplayName("rawPassword와 일치하면, true를 반환한다.")
        @Test
        fun returnsTrue_whenPasswordMatches() {
            // arrange
            val rawPassword = "Password1!"
            val user = createUser(rawPassword = rawPassword)

            // act
            val result = user.isCorrectPasswd(rawPassword)

            // assert
            assertThat(result).isTrue()
        }

        @DisplayName("rawPassword와 다르면, false를 반환한다.")
        @Test
        fun returnsFalse_whenPasswordDoesNotMatch() {
            // arrange
            val rawPassword = "Password1!"
            val user = createUser(rawPassword = rawPassword)

            // act
            val result = user.isCorrectPasswd("WrongPassword1!")

            // assert
            assertThat(result).isFalse()
        }
    }

    @DisplayName("비밀번호를 변경할 때, ")
    @Nested
    inner class ChangePw {

        @DisplayName("이전 비밀번호가 올바르고 새 비밀번호가 유효하면, 암호화되어 변경된다.")
        @Test
        fun changesPassword_whenPrevPasswordIsCorrectAndNextIsValid() {
            // arrange
            val prevPw = "Password1!"
            val nextPw = "NewPass1!!"
            val user = createUser(rawPassword = prevPw)

            // act
            val result = user.changePw(prevPw, nextPw)

            // assert
            assertThat(result.password).isNotEqualTo(nextPw)
            assertThat(result.isCorrectPasswd(nextPw)).isTrue()
            assertThat(result.isCorrectPasswd(prevPw)).isFalse()
        }

        @DisplayName("이전 비밀번호가 틀리면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        fun throwsUnauthorized_whenPrevPasswordIsIncorrect() {
            // arrange
            val user = createUser(rawPassword = "Password1!")

            // act
            val result = assertThrows<CoreException> {
                user.changePw("WrongPass1!", "NewPass1!!")
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }

        @DisplayName("새 비밀번호가 8자 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenNextPasswordIsTooShort() {
            // arrange
            val prevPw = "Password1!"
            val user = createUser(rawPassword = prevPw)

            // act
            val result = assertThrows<CoreException> {
                user.changePw(prevPw, "Short1!")
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("새 비밀번호가 16자를 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenNextPasswordIsTooLong() {
            // arrange
            val prevPw = "Password1!"
            val user = createUser(rawPassword = prevPw)

            // act
            val result = assertThrows<CoreException> {
                user.changePw(prevPw, "Password1!abcdefg")
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("새 비밀번호에 생년월일이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenNextPasswordContainsBirthday() {
            // arrange
            val prevPw = "Password1!"
            val user = createUser(rawPassword = prevPw, birth = LocalDate.of(2000, 1, 1))

            // act
            val result = assertThrows<CoreException> {
                user.changePw(prevPw, "a20000101!")
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("새 비밀번호에 허용되지 않은 문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenNextPasswordContainsInvalidCharacters() {
            // arrange
            val prevPw = "Password1!"
            val user = createUser(rawPassword = prevPw)

            // act
            val result = assertThrows<CoreException> {
                user.changePw(prevPw, "Password한글1!")
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("비밀번호 변경 후 다른 필드는 변경되지 않는다.")
        @Test
        fun preservesOtherFields_whenPasswordIsChanged() {
            // arrange
            val prevPw = "Password1!"
            val nextPw = "NewPass1!!"
            val user = createUser(rawPassword = prevPw)

            // act
            val result = user.changePw(prevPw, nextPw)

            // assert
            assertThat(result.loginId).isEqualTo(user.loginId)
            assertThat(result.name).isEqualTo(user.name)
            assertThat(result.birth).isEqualTo(user.birth)
            assertThat(result.email).isEqualTo(user.email)
        }
    }
}
