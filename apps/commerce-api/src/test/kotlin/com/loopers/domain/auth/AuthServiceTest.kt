package com.loopers.domain.auth

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
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

    private val defaultBirth: LocalDate = LocalDate.of(2000, 1, 1)

    @BeforeEach
    fun setUp() {
        authRepositoryPort = mockk()
        authService = AuthService(authRepositoryPort)
    }

    private fun authOf(
        userId: Long = 1L,
        loginId: String = "testuser",
        rawPassword: String = "Password1!",
        birth: LocalDate = defaultBirth,
    ): Auth = Auth.create(userId = userId, loginId = loginId, rawPassword = rawPassword, birth = birth)

    @DisplayName("로그인할 때, ")
    @Nested
    inner class Login {
        @DisplayName("존재하는 아이디와 올바른 비밀번호가 주어지면, userId를 반환한다.")
        @Test
        fun returnsUserId_whenLoginIdAndPasswordAreCorrect() {
            val loginId = "testuser"
            val rawPassword = "Password1!"
            val auth = authOf(userId = 42L, loginId = loginId, rawPassword = rawPassword)
            every { authRepositoryPort.findByLoginId(loginId) } returns auth

            val result = authService.login(loginId, rawPassword)

            assertThat(result).isEqualTo(42L)
        }

        @DisplayName("존재하지 않는 아이디가 주어지면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        fun throwsUnauthorized_whenLoginIdDoesNotExist() {
            every { authRepositoryPort.findByLoginId(any()) } returns null

            val result = assertThrows<CoreException> {
                authService.login("unknown", "Password1!")
            }

            assertThat(result.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }

        @DisplayName("비밀번호가 일치하지 않으면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        fun throwsUnauthorized_whenPasswordDoesNotMatch() {
            val auth = authOf(loginId = "testuser", rawPassword = "Password1!")
            every { authRepositoryPort.findByLoginId("testuser") } returns auth

            val result = assertThrows<CoreException> {
                authService.login("testuser", "WrongPass1!")
            }

            assertThat(result.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }

        @DisplayName("아이디가 없을 때와 비밀번호가 틀릴 때 메시지가 동일하다(보안).")
        @Test
        fun returnsSameMessage_whenLoginIdMissingOrPasswordWrong() {
            val auth = authOf(loginId = "testuser", rawPassword = "Password1!")
            every { authRepositoryPort.findByLoginId("testuser") } returns auth
            every { authRepositoryPort.findByLoginId("unknown") } returns null

            val byMissingId = assertThrows<CoreException> {
                authService.login("unknown", "Password1!")
            }
            val byWrongPassword = assertThrows<CoreException> {
                authService.login("testuser", "WrongPass1!")
            }

            assertThat(byMissingId.message).isEqualTo(byWrongPassword.message)
        }
    }

    @DisplayName("register를 호출할 때, ")
    @Nested
    inner class Register {
        @DisplayName("loginId가 중복되지 않으면, Auth가 저장된다.")
        @Test
        fun savesAuth_whenLoginIdIsUnique() {
            every { authRepositoryPort.existsByLoginId("testuser01") } returns false
            val authSlot = slot<Auth>()
            every { authRepositoryPort.save(capture(authSlot)) } answers { authSlot.captured.copy(id = 1L) }

            val result = authService.register(
                userId = 100L,
                loginId = "testuser01",
                rawPassword = "Password1!",
                birth = defaultBirth,
            )

            assertThat(result.id).isEqualTo(1L)
            assertThat(authSlot.captured.userId).isEqualTo(100L)
            assertThat(authSlot.captured.loginId).isEqualTo("testuser01")
            assertThat(authSlot.captured.matches("Password1!")).isTrue()
            verify(exactly = 1) { authRepositoryPort.existsByLoginId("testuser01") }
            verify(exactly = 1) { authRepositoryPort.save(any()) }
        }

        @DisplayName("loginId가 이미 존재하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenLoginIdExists() {
            every { authRepositoryPort.existsByLoginId("testuser01") } returns true

            val result = assertThrows<CoreException> {
                authService.register(
                    userId = 100L,
                    loginId = "testuser01",
                    rawPassword = "Password1!",
                    birth = defaultBirth,
                )
            }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            verify(exactly = 0) { authRepositoryPort.save(any()) }
        }
    }

    @DisplayName("changePassword를 호출할 때, ")
    @Nested
    inner class ChangePassword {
        @DisplayName("이전 비밀번호가 맞으면, Auth가 갱신된다.")
        @Test
        fun updatesAuth_whenPrevPwMatches() {
            val existingAuth = authOf(userId = 100L, loginId = "testuser01", rawPassword = "Password1!")
            every { authRepositoryPort.findByUserId(100L) } returns existingAuth
            val savedSlot = slot<Auth>()
            every { authRepositoryPort.save(capture(savedSlot)) } answers { savedSlot.captured }

            authService.changePassword(userId = 100L, prevPw = "Password1!", nextPw = "NewPass1!!", birth = defaultBirth)

            assertThat(savedSlot.captured.matches("NewPass1!!")).isTrue()
            verify(exactly = 1) { authRepositoryPort.save(any()) }
        }

        @DisplayName("이전 비밀번호가 틀리면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        fun throwsUnauthorized_whenPrevPwWrong() {
            val existingAuth = authOf(userId = 100L, loginId = "testuser01", rawPassword = "Password1!")
            every { authRepositoryPort.findByUserId(100L) } returns existingAuth

            val result = assertThrows<CoreException> {
                authService.changePassword(
                    userId = 100L,
                    prevPw = "WrongPrev1!",
                    nextPw = "NewPass1!!",
                    birth = defaultBirth,
                )
            }

            assertThat(result.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
            verify(exactly = 0) { authRepositoryPort.save(any()) }
        }

        @DisplayName("Auth가 없으면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenAuthMissing() {
            every { authRepositoryPort.findByUserId(100L) } returns null

            val result = assertThrows<CoreException> {
                authService.changePassword(userId = 100L, prevPw = "x", nextPw = "y", birth = defaultBirth)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }
}
