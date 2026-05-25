package com.loopers.domain.auth

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class AuthServiceTest {
    private lateinit var authRepositoryPort: AuthRepositoryPort
    private lateinit var authService: AuthService

    @BeforeEach
    fun setUp() {
        authRepositoryPort = mockk()
        authService = AuthService(authRepositoryPort)
    }

    private fun authOf(
        userId: Long = 1L,
        loginId: String = "testuser",
        rawPassword: String = "Password1!",
        birth: LocalDate = LocalDate.of(2000, 1, 1),
    ): Auth = Auth.create(userId = userId, loginId = loginId, rawPassword = rawPassword, birth = birth)

    @DisplayName("로그인할 때, ")
    @Nested
    inner class Login {
        @DisplayName("존재하는 아이디와 올바른 비밀번호가 주어지면, userId를 반환한다.")
        @Test
        fun returnsUserId_whenLoginIdAndPasswordAreCorrect() {
            // arrange
            val loginId = "testuser"
            val rawPassword = "Password1!"
            val auth = authOf(userId = 42L, loginId = loginId, rawPassword = rawPassword)
            every { authRepositoryPort.findByLoginIdOrNull(loginId) } returns auth

            // act
            val result = authService.login(loginId, rawPassword)

            // assert
            assertThat(result).isEqualTo(42L)
        }

        @DisplayName("존재하지 않는 아이디가 주어지면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        fun throwsUnauthorized_whenLoginIdDoesNotExist() {
            // arrange
            every { authRepositoryPort.findByLoginIdOrNull(any()) } returns null

            // act
            val result = assertThrows<CoreException> {
                authService.login("unknown", "Password1!")
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }

        @DisplayName("비밀번호가 일치하지 않으면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        fun throwsUnauthorized_whenPasswordDoesNotMatch() {
            // arrange
            val auth = authOf(loginId = "testuser", rawPassword = "Password1!")
            every { authRepositoryPort.findByLoginIdOrNull("testuser") } returns auth

            // act
            val result = assertThrows<CoreException> {
                authService.login("testuser", "WrongPass1!")
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }

        @DisplayName("아이디가 없을 때와 비밀번호가 틀릴 때 메시지가 동일하다(보안).")
        @Test
        fun returnsSameMessage_whenLoginIdMissingOrPasswordWrong() {
            // arrange
            val auth = authOf(loginId = "testuser", rawPassword = "Password1!")
            every { authRepositoryPort.findByLoginIdOrNull("testuser") } returns auth
            every { authRepositoryPort.findByLoginIdOrNull("unknown") } returns null

            // act
            val byMissingId = assertThrows<CoreException> {
                authService.login("unknown", "Password1!")
            }
            val byWrongPassword = assertThrows<CoreException> {
                authService.login("testuser", "WrongPass1!")
            }

            // assert
            assertThat(byMissingId.message).isEqualTo(byWrongPassword.message)
        }
    }
}
