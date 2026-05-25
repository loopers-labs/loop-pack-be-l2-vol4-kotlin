package com.loopers.application.user

import com.loopers.domain.auth.Auth
import com.loopers.domain.auth.AuthRepositoryPort
import com.loopers.domain.auth.AuthService
import com.loopers.domain.user.User
import com.loopers.domain.user.UserRepositoryPort
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

class UserFacadeTest {
    private lateinit var userRepositoryPort: UserRepositoryPort
    private lateinit var authRepositoryPort: AuthRepositoryPort
    private lateinit var authService: AuthService
    private lateinit var userFacade: UserFacade

    private val defaultBirth: LocalDate = LocalDate.of(1995, 3, 15)

    @BeforeEach
    fun setUp() {
        userRepositoryPort = mockk()
        authRepositoryPort = mockk()
        authService = mockk()
        userFacade = UserFacade(userRepositoryPort, authRepositoryPort, authService)
    }

    private fun signupCommand(
        loginId: String = "testuser01",
        rawPassword: String = "Password1!",
        name: String = "홍길동",
        birth: LocalDate = defaultBirth,
        email: String = "test@example.com",
    ): SignupCommand = SignupCommand(
        loginId = loginId,
        rawPassword = rawPassword,
        name = name,
        birth = birth,
        email = email,
    )

    private fun changePwCommand(
        loginId: String = "testuser01",
        loginPw: String = "Password1!",
        prevPw: String = "Password1!",
        nextPw: String = "NewPass1!!",
    ): ChangePwCommand = ChangePwCommand(loginId = loginId, loginPw = loginPw, prevPw = prevPw, nextPw = nextPw)

    @DisplayName("signup을 호출할 때, ")
    @Nested
    inner class Signup {
        @DisplayName("loginId가 중복되지 않으면, User와 Auth가 모두 저장된다.")
        @Test
        fun savesUserAndAuth_whenLoginIdIsUnique() {
            // arrange
            val command = signupCommand()
            every { authRepositoryPort.existsByLoginId(command.loginId) } returns false
            val savedUser = User(id = 100L, name = command.name, birth = command.birth, email = command.email)
            every { userRepositoryPort.save(any()) } returns savedUser
            val authSlot = slot<Auth>()
            every { authRepositoryPort.save(capture(authSlot)) } answers { authSlot.captured.copy(id = 1L) }

            // act
            val result = userFacade.signup(command)

            // assert
            assertThat(result).isEqualTo(savedUser)
            verify(exactly = 1) { authRepositoryPort.existsByLoginId(command.loginId) }
            verify(exactly = 1) { userRepositoryPort.save(any()) }
            verify(exactly = 1) { authRepositoryPort.save(any()) }
            assertThat(authSlot.captured.userId).isEqualTo(100L)
            assertThat(authSlot.captured.loginId).isEqualTo(command.loginId)
        }

        @DisplayName("loginId가 이미 존재하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenLoginIdExists() {
            // arrange
            val command = signupCommand()
            every { authRepositoryPort.existsByLoginId(command.loginId) } returns true

            // act
            val result = assertThrows<CoreException> { userFacade.signup(command) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            verify(exactly = 0) { userRepositoryPort.save(any()) }
            verify(exactly = 0) { authRepositoryPort.save(any()) }
        }
    }

    @DisplayName("getMyInfo를 호출할 때, ")
    @Nested
    inner class GetMyInfo {
        @DisplayName("인증에 성공하면, 사용자 정보를 반환한다.")
        @Test
        fun returnsInfo_whenAuthenticated() {
            // arrange
            val loginId = "testuser01"
            val rawPassword = "Password1!"
            every { authService.login(loginId, rawPassword) } returns 100L
            val user = User(id = 100L, name = "홍길동", birth = defaultBirth, email = "test@example.com")
            every { userRepositoryPort.findByIdOrNull(100L) } returns user

            // act
            val result = userFacade.getMyInfo(loginId, rawPassword)

            // assert
            assertThat(result.loginId).isEqualTo(loginId)
            assertThat(result.name).isEqualTo("홍길동")
            assertThat(result.email).isEqualTo("test@example.com")
            assertThat(result.birth).isEqualTo(defaultBirth)
        }

        @DisplayName("인증에 실패하면, UNAUTHORIZED 예외가 전파된다.")
        @Test
        fun throwsUnauthorized_whenAuthFails() {
            // arrange
            every { authService.login(any(), any()) } throws CoreException(ErrorType.UNAUTHORIZED, "no")

            // act
            val result = assertThrows<CoreException> { userFacade.getMyInfo("x", "y") }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }

        @DisplayName("인증 성공 후 사용자를 찾을 수 없으면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenUserMissing() {
            // arrange
            every { authService.login(any(), any()) } returns 100L
            every { userRepositoryPort.findByIdOrNull(100L) } returns null

            // act
            val result = assertThrows<CoreException> { userFacade.getMyInfo("x", "y") }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("changePassword를 호출할 때, ")
    @Nested
    inner class ChangePassword {
        @DisplayName("인증과 비밀번호 변경이 성공하면, Auth가 갱신된다.")
        @Test
        fun updatesAuth_whenAllConditionsAreMet() {
            // arrange
            val command = changePwCommand()
            every { authService.login(command.loginId, command.loginPw) } returns 100L
            val user = User(id = 100L, name = "홍길동", birth = defaultBirth, email = "test@example.com")
            every { userRepositoryPort.findByIdOrNull(100L) } returns user
            val existingAuth = Auth.create(userId = 100L, loginId = command.loginId, rawPassword = command.prevPw, birth = defaultBirth)
            every { authRepositoryPort.findByUserIdOrNull(100L) } returns existingAuth
            val savedSlot = slot<Auth>()
            every { authRepositoryPort.save(capture(savedSlot)) } answers { savedSlot.captured }

            // act
            userFacade.changePassword(command)

            // assert
            assertThat(savedSlot.captured.matches(command.nextPw)).isTrue()
            verify(exactly = 1) { authRepositoryPort.save(any()) }
        }

        @DisplayName("인증에 실패하면, UNAUTHORIZED 예외가 전파된다.")
        @Test
        fun throwsUnauthorized_whenAuthFails() {
            // arrange
            val command = changePwCommand()
            every { authService.login(command.loginId, command.loginPw) } throws CoreException(ErrorType.UNAUTHORIZED, "no")

            // act
            val result = assertThrows<CoreException> { userFacade.changePassword(command) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
            verify(exactly = 0) { authRepositoryPort.save(any()) }
        }

        @DisplayName("이전 비밀번호가 틀리면, Auth 도메인에서 UNAUTHORIZED 예외가 발생한다.")
        @Test
        fun throwsUnauthorized_whenPrevPwWrong() {
            // arrange
            val command = changePwCommand(prevPw = "WrongPrev1!")
            every { authService.login(command.loginId, command.loginPw) } returns 100L
            val user = User(id = 100L, name = "홍길동", birth = defaultBirth, email = "test@example.com")
            every { userRepositoryPort.findByIdOrNull(100L) } returns user
            val existingAuth = Auth.create(userId = 100L, loginId = command.loginId, rawPassword = "Password1!", birth = defaultBirth)
            every { authRepositoryPort.findByUserIdOrNull(100L) } returns existingAuth

            // act
            val result = assertThrows<CoreException> { userFacade.changePassword(command) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
            verify(exactly = 0) { authRepositoryPort.save(any()) }
        }
    }
}
