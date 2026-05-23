package com.loopers.application.user

import com.loopers.domain.user.User
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class UserFacadeTest {
    private val userService: UserService = mockk()
    private val userFacade = UserFacade(userService)

    private fun createUser(
        loginId: String = "testuser01",
        rawPassword: String = "Password1!",
        name: String = "홍길동",
        birth: LocalDate = LocalDate.of(1995, 3, 15),
        email: String = "test@example.com",
    ): User = User.create(
        loginId = loginId,
        rawPassword = rawPassword,
        name = name,
        birth = birth,
        email = email,
    )

    private fun createCommand(
        loginId: String = "testuser01",
        loginPw: String = "Password1!",
        prevPw: String = "Password1!",
        nextPw: String = "NewPassword1!",
    ): ChangePwCommand = ChangePwCommand(
        loginId = loginId,
        loginPw = loginPw,
        prevPw = prevPw,
        nextPw = nextPw,
    )

    @DisplayName("changePw를 호출할 때,")
    @Nested
    inner class ChangePw {

        @DisplayName("로그인과 비밀번호 변경이 성공하면, changePw가 호출된다.")
        @Test
        fun callsChangePw_whenLoginSucceeds() {
            // arrange
            val command = createCommand()
            val user = createUser()
            every { userService.login(command.loginId, command.loginPw) } returns user
            every { userService.changePw(command) } returns Unit

            // act
            userFacade.changePw(command)

            // assert
            verify(exactly = 1) { userService.login(command.loginId, command.loginPw) }
            verify(exactly = 1) { userService.changePw(command) }
        }

        @DisplayName("로그인 인증에 실패하면, UNAUTHORIZED 에러가 발생한다.")
        @Test
        fun throwsUnauthorized_whenLoginFails() {
            // arrange
            val command = createCommand()
            every { userService.login(command.loginId, command.loginPw) } throws CoreException(
                ErrorType.UNAUTHORIZED,
                "아이디 또는 비밀번호가 올바르지 않습니다.",
            )

            // act
            val exception = assertThrows<CoreException> {
                userFacade.changePw(command)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
            verify(exactly = 1) { userService.login(command.loginId, command.loginPw) }
            verify(exactly = 0) { userService.changePw(any()) }
        }

        @DisplayName("로그인 성공 후 비밀번호 변경에서 BAD_REQUEST가 발생하면, BAD_REQUEST 에러가 발생한다.")
        @Test
        fun throwsBadRequest_whenChangePwValidationFails() {
            // arrange
            val command = createCommand(nextPw = "short")
            val user = createUser()
            every { userService.login(command.loginId, command.loginPw) } returns user
            every { userService.changePw(command) } throws CoreException(
                ErrorType.BAD_REQUEST,
                "비밀번호는 8~16자여야 합니다.",
            )

            // act
            val exception = assertThrows<CoreException> {
                userFacade.changePw(command)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            verify(exactly = 1) { userService.login(command.loginId, command.loginPw) }
            verify(exactly = 1) { userService.changePw(command) }
        }

        @DisplayName("로그인 성공 후 이전 비밀번호가 틀리면, UNAUTHORIZED 에러가 발생한다.")
        @Test
        fun throwsUnauthorized_whenPrevPwIsWrong() {
            // arrange
            val command = createCommand(prevPw = "WrongPrevPw1!")
            val user = createUser()
            every { userService.login(command.loginId, command.loginPw) } returns user
            every { userService.changePw(command) } throws CoreException(
                ErrorType.UNAUTHORIZED,
                "이전 비밀번호가 올바르지 않습니다.",
            )

            // act
            val exception = assertThrows<CoreException> {
                userFacade.changePw(command)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
            verify(exactly = 1) { userService.login(command.loginId, command.loginPw) }
            verify(exactly = 1) { userService.changePw(command) }
        }
    }
}
