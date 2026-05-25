package com.loopers.domain.auth

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class AuthTest {
    private fun createAuth(
        userId: Long = 1L,
        loginId: String = "testuser",
        rawPassword: String = "Password1!",
        birth: LocalDate = LocalDate.of(2000, 1, 1),
    ): Auth = Auth.create(
        userId = userId,
        loginId = loginId,
        rawPassword = rawPassword,
        birth = birth,
    )

    @DisplayName("Auth 자격증명을 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("영문과 숫자로만 이루어진 아이디면, 정상적으로 생성된다.")
        @Test
        fun createsAuth_whenLoginIdIsAlphanumeric() {
            // arrange
            val loginId = "testUser123"

            // act
            val auth = createAuth(loginId = loginId)

            // assert
            assertThat(auth.loginId).isEqualTo(loginId)
            assertThat(auth.userId).isEqualTo(1L)
        }

        @DisplayName("아이디에 한글이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenLoginIdContainsKorean() {
            // act
            val result = assertThrows<CoreException> {
                createAuth(loginId = "test유저")
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("아이디에 특수문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenLoginIdContainsSpecialCharacters() {
            // act
            val result = assertThrows<CoreException> {
                createAuth(loginId = "test@user!")
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("아이디가 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenLoginIdIsEmpty() {
            // act
            val result = assertThrows<CoreException> {
                createAuth(loginId = "")
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("비밀번호가 정책에 부합하면, 암호화되어 저장된다.")
        @Test
        fun encodesPassword_whenValidInfoIsProvided() {
            // arrange
            val rawPassword = "Password1!"

            // act
            val auth = createAuth(rawPassword = rawPassword)

            // assert
            assertThat(auth.password.value).isNotEqualTo(rawPassword)
            assertThat(auth.matches(rawPassword)).isTrue()
        }
    }

    @DisplayName("비밀번호를 확인할 때, ")
    @Nested
    inner class Matches {
        @DisplayName("rawPassword와 일치하면, true를 반환한다.")
        @Test
        fun returnsTrue_whenPasswordMatches() {
            // arrange
            val rawPassword = "Password1!"
            val auth = createAuth(rawPassword = rawPassword)

            // act
            val result = auth.matches(rawPassword)

            // assert
            assertThat(result).isTrue()
        }

        @DisplayName("rawPassword와 다르면, false를 반환한다.")
        @Test
        fun returnsFalse_whenPasswordDoesNotMatch() {
            // arrange
            val auth = createAuth(rawPassword = "Password1!")

            // act
            val result = auth.matches("WrongPassword1!")

            // assert
            assertThat(result).isFalse()
        }
    }

    @DisplayName("비밀번호를 변경할 때, ")
    @Nested
    inner class ChangePassword {
        @DisplayName("이전 비밀번호가 맞고 새 비밀번호가 유효하면, 새 비밀번호가 적용된다.")
        @Test
        fun changesPassword_whenPrevIsCorrectAndNextIsValid() {
            // arrange
            val prev = "Password1!"
            val next = "NewPass1!!"
            val birth = LocalDate.of(2000, 1, 1)
            val auth = createAuth(rawPassword = prev, birth = birth)

            // act
            val updated = auth.changePassword(prev, next, birth)

            // assert
            assertThat(updated.matches(next)).isTrue()
            assertThat(updated.matches(prev)).isFalse()
        }

        @DisplayName("이전 비밀번호가 틀리면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        fun throwsUnauthorized_whenPrevIsIncorrect() {
            // arrange
            val birth = LocalDate.of(2000, 1, 1)
            val auth = createAuth(rawPassword = "Password1!", birth = birth)

            // act
            val result = assertThrows<CoreException> {
                auth.changePassword("WrongPass1!", "NewPass1!!", birth)
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }

        @DisplayName("새 비밀번호가 8자 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenNextIsTooShort() {
            // arrange
            val birth = LocalDate.of(2000, 1, 1)
            val prev = "Password1!"
            val auth = createAuth(rawPassword = prev, birth = birth)

            // act
            val result = assertThrows<CoreException> {
                auth.changePassword(prev, "Short1!", birth)
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("새 비밀번호에 생년월일이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenNextContainsBirthday() {
            // arrange
            val birth = LocalDate.of(2000, 1, 1)
            val prev = "Password1!"
            val auth = createAuth(rawPassword = prev, birth = birth)

            // act
            val result = assertThrows<CoreException> {
                auth.changePassword(prev, "a20000101!", birth)
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("비밀번호 변경 후 loginId, userId는 보존된다.")
        @Test
        fun preservesOtherFields() {
            // arrange
            val prev = "Password1!"
            val next = "NewPass1!!"
            val birth = LocalDate.of(2000, 1, 1)
            val auth = createAuth(loginId = "testuser", userId = 42L, rawPassword = prev, birth = birth)

            // act
            val updated = auth.changePassword(prev, next, birth)

            // assert
            assertThat(updated.loginId).isEqualTo("testuser")
            assertThat(updated.userId).isEqualTo(42L)
        }
    }
}
