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
    @DisplayName("유저를 생성할 때, ")
    @Nested
    inner class Create {
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
                password = PasswordEncryptionUtil.encode(rawPassword),
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
}
