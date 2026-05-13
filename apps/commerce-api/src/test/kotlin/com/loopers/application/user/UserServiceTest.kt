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

class UserServiceTest {
    private val userRepository: UserRepository = mockk()
    private val userService = UserService(userRepository)

    private fun createUser(
        loginId: String = "testuser01",
        rawPassword: String = "password1234",
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

    @DisplayName("login을 호출할 때,")
    @Nested
    inner class Login {

        @DisplayName("findByLoginId로 User가 반환되고 login이 true를 반환하면, User를 반환한다.")
        @Test
        fun returnsUser_whenLoginSucceeds() {
            // arrange
            val loginId = "testuser01"
            val password = "password1234"
            val user = mockk<User>()
            every { userRepository.findByLoginId(loginId) } returns user
            every { user.isCorrectPasswd(password) } returns true

            // act
            val result = userService.login(loginId, password)

            // assert
            assertThat(result).isEqualTo(user)
        }

        @DisplayName("findByLoginId가 에러를 던지면, 인증 에러가 발생한다.")
        @Test
        fun throwsUnauthorized_whenUserNotFound() {
            // arrange
            val loginId = "nonexistent"
            val password = "password1234"
            every { userRepository.findByLoginId(loginId) } throws CoreException(ErrorType.NOT_FOUND)

            // act
            val exception = assertThrows<CoreException> {
                userService.login(loginId, password)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }

        @DisplayName("findByLoginId로 User가 반환되고 login이 false를 반환하면, 인증 에러가 발생한다.")
        @Test
        fun throwsUnauthorized_whenPasswordNotMatched() {
            // arrange
            val loginId = "testuser01"
            val password = "wrongPassword1"
            val user = mockk<User>()
            every { userRepository.findByLoginId(loginId) } returns user
            every { user.isCorrectPasswd(password) } returns false

            // act
            val exception = assertThrows<CoreException> {
                userService.login(loginId, password)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }

        @DisplayName("loginId가 빈 값이면, 인증 에러가 발생한다.")
        @Test
        fun throwsUnauthorized_whenLoginIdIsEmpty() {
            // arrange
            val loginId = ""
            val password = "password1234"
            every { userRepository.findByLoginId(loginId) } throws CoreException(ErrorType.NOT_FOUND)

            // act
            val exception = assertThrows<CoreException> {
                userService.login(loginId, password)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }

        @DisplayName("password가 빈 값이면, 인증 에러가 발생한다.")
        @Test
        fun throwsUnauthorized_whenPasswordIsEmpty() {
            // arrange
            val loginId = "testuser01"
            val password = ""
            val user = mockk<User>()
            every { userRepository.findByLoginId(loginId) } returns user
            every { user.isCorrectPasswd(password) } returns false

            // act
            val exception = assertThrows<CoreException> {
                userService.login(loginId, password)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }
    }

    @DisplayName("changePw을 호출할 때,")
    @Nested
    inner class ChangePw {

        @DisplayName("정상적으로 비밀번호가 변경된다.")
        @Test
        fun changesPasswordSuccessfully() {
            // arrange
            val command = ChangePwCommand(
                loginId = "testuser01",
                loginPw = "password1234",
                prevPw = "password1234",
                nextPw = "newPassword1!",
            )
            val user = mockk<User>()
            val updatedUser = mockk<User>()
            every { userRepository.findByLoginId(command.loginId) } returns user
            every { user.changePw(command.prevPw, command.nextPw) } returns updatedUser
            every { userRepository.update(updatedUser) } returns updatedUser

            // act
            userService.changePw(command)

            // assert
            verify { userRepository.findByLoginId(command.loginId) }
            verify { user.changePw(command.prevPw, command.nextPw) }
            verify { userRepository.update(updatedUser) }
        }

        @DisplayName("존재하지 않는 loginId이면, UNAUTHORIZED 에러가 발생한다.")
        @Test
        fun throwsUnauthorized_whenUserNotFound() {
            // arrange
            val command = ChangePwCommand(
                loginId = "nonexistent",
                loginPw = "password1234",
                prevPw = "password1234",
                nextPw = "newPassword1!",
            )
            every { userRepository.findByLoginId(command.loginId) } throws CoreException(ErrorType.NOT_FOUND)

            // act
            val exception = assertThrows<CoreException> {
                userService.changePw(command)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }

        @DisplayName("이전 비밀번호가 틀리면, UNAUTHORIZED 에러가 발생한다.")
        @Test
        fun throwsUnauthorized_whenPrevPwIsWrong() {
            // arrange
            val command = ChangePwCommand(
                loginId = "testuser01",
                loginPw = "password1234",
                prevPw = "wrongPassword1",
                nextPw = "newPassword1!",
            )
            val user = mockk<User>()
            every { userRepository.findByLoginId(command.loginId) } returns user
            every { user.changePw(command.prevPw, command.nextPw) } throws CoreException(ErrorType.UNAUTHORIZED, "이전 비밀번호가 올바르지 않습니다.")

            // act
            val exception = assertThrows<CoreException> {
                userService.changePw(command)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }

        @DisplayName("새 비밀번호가 유효하지 않으면, BAD_REQUEST 에러가 발생한다.")
        @Test
        fun throwsBadRequest_whenNextPwIsInvalid() {
            // arrange
            val command = ChangePwCommand(
                loginId = "testuser01",
                loginPw = "password1234",
                prevPw = "password1234",
                nextPw = "short",
            )
            val user = mockk<User>()
            every { userRepository.findByLoginId(command.loginId) } returns user
            every { user.changePw(command.prevPw, command.nextPw) } throws CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8~16자여야 합니다.")

            // act
            val exception = assertThrows<CoreException> {
                userService.changePw(command)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("signup을 호출할 때,")
    @Nested
    inner class Signup {

        @DisplayName("UserRepository의 findByLoginId가 실행된다.")
        @Test
        fun callsFindByLoginId() {
            // arrange
            val user = createUser()
            every { userRepository.findByLoginId(user.loginId) } throws CoreException(ErrorType.NOT_FOUND)
            every { userRepository.save(user) } returns user

            // act
            userService.signup(user)

            // assert
            verify { userRepository.findByLoginId(user.loginId) }
        }

        @DisplayName("findByLoginId가 유저를 찾으면, BAD_REQUEST 에러가 발생한다.")
        @Test
        fun throwsBadRequest_whenLoginIdAlreadyExists() {
            // arrange
            val user = createUser()
            every { userRepository.findByLoginId(user.loginId) } returns user

            // act
            val exception = assertThrows<CoreException> {
                userService.signup(user)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("UserRepository의 save가 실행된다.")
        @Test
        fun callsSave() {
            // arrange
            val user = createUser()
            every { userRepository.findByLoginId(user.loginId) } throws CoreException(ErrorType.NOT_FOUND)
            every { userRepository.save(user) } returns user

            // act
            userService.signup(user)

            // assert
            verify { userRepository.save(user) }
        }
    }
}
