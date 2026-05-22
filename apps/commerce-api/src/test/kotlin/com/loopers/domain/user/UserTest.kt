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

class UserTest {
    @DisplayName("User 를 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("유효한 입력값이 주어지면, 동일한 필드를 가진 User 가 만들어진다.")
        @Test
        fun buildsUserWithValidFields() {
            val user = User(
                loginId = "loopers01",
                encryptedPassword = "hashed",
                name = "홍길동",
                birthdate = LocalDate.of(1990, 1, 1),
                email = "user@example.com",
            )

            assertAll(
                { assertThat(user.loginId).isEqualTo("loopers01") },
                { assertThat(user.encryptedPassword).isEqualTo("hashed") },
                { assertThat(user.name).isEqualTo("홍길동") },
                { assertThat(user.birthdate).isEqualTo(LocalDate.of(1990, 1, 1)) },
                { assertThat(user.email).isEqualTo("user@example.com") },
            )
        }

        @DisplayName("loginId 가 영숫자 4~20자 패턴을 벗어나면, BAD_REQUEST 예외를 던진다.")
        @Test
        fun rejectsInvalidLoginId() {
            val ex = assertThrows<CoreException> {
                User(
                    loginId = "한글아이디",
                    encryptedPassword = "hashed",
                    name = "홍길동",
                    birthdate = LocalDate.of(1990, 1, 1),
                    email = "user@example.com",
                )
            }

            assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("name 이 공백으로만 이루어지면, BAD_REQUEST 예외를 던진다.")
        @Test
        fun rejectsBlankName() {
            val ex = assertThrows<CoreException> {
                User(
                    loginId = "loopers01",
                    encryptedPassword = "hashed",
                    name = "   ",
                    birthdate = LocalDate.of(1990, 1, 1),
                    email = "user@example.com",
                )
            }

            assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("email 이 형식 규칙을 위반하면, BAD_REQUEST 예외를 던진다.")
        @Test
        fun rejectsInvalidEmail() {
            val ex = assertThrows<CoreException> {
                User(
                    loginId = "loopers01",
                    encryptedPassword = "hashed",
                    name = "홍길동",
                    birthdate = LocalDate.of(1990, 1, 1),
                    email = "not-an-email",
                )
            }

            assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("birthdate 가 미래 날짜이면, BAD_REQUEST 예외를 던진다.")
        @Test
        fun rejectsFutureBirthdate() {
            val ex = assertThrows<CoreException> {
                User(
                    loginId = "loopers01",
                    encryptedPassword = "hashed",
                    name = "홍길동",
                    birthdate = LocalDate.now().plusDays(1),
                    email = "user@example.com",
                )
            }

            assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("changePassword 를 호출하면, encryptedPassword 가 새 해시로 교체된다.")
    @Test
    fun changePasswordOverwritesEncryptedPassword() {
        val user = User(
            loginId = "loopers01",
            encryptedPassword = "old-hash",
            name = "홍길동",
            birthdate = LocalDate.of(1990, 1, 1),
            email = "user@example.com",
        )

        user.changePassword("new-hash")

        assertThat(user.encryptedPassword).isEqualTo("new-hash")
    }
}
