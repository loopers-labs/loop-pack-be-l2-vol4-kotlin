package com.loopers.application.user

import com.loopers.domain.user.User
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate

@SpringBootTest
class UserFacadeIntegrationTest @Autowired constructor(
    private val userFacade: UserFacade,
    private val userService: UserService,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

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

    private fun createChangePwCommand(
        loginId: String = "testuser01",
        loginPw: String = "password1234",
        prevPw: String = "password1234",
        nextPw: String = "newPassword1!",
    ): ChangePwCommand = ChangePwCommand(
        loginId = loginId,
        loginPw = loginPw,
        prevPw = prevPw,
        nextPw = nextPw,
    )

    @DisplayName("changePw를 호출할 때,")
    @Nested
    inner class ChangePw {

        @DisplayName("로그인 정보가 올바르고 prevPw가 맞고 nextPw가 유효하면, 비밀번호가 변경된다.")
        @Test
        fun changesPassword_whenAllConditionsAreMet() {
            // arrange
            val rawPassword = "password1234"
            val nextPw = "newPassword1!"
            val user = createUser(rawPassword = rawPassword)
            userService.signup(user)

            val command = createChangePwCommand(
                loginPw = rawPassword,
                prevPw = rawPassword,
                nextPw = nextPw,
            )

            // act
            userFacade.changePw(command)

            // assert
            val updatedUser = userService.login(user.loginId, nextPw)
            assertThat(updatedUser.loginId).isEqualTo(user.loginId)
        }

        @DisplayName("로그인 정보가 올바르지 않으면, 인증 에러가 발생한다.")
        @Test
        fun throwsUnauthorized_whenLoginPwIsInvalid() {
            // arrange
            val user = createUser()
            userService.signup(user)

            val command = createChangePwCommand(
                loginId = "testuser01",
                loginPw = "wrongPassword1!",
                prevPw = "password1234",
                nextPw = "newPassword1!",
            )

            // act
            val exception = assertThrows<CoreException> {
                userFacade.changePw(command)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }

        @DisplayName("로그인 정보는 올바르지만 prevPw가 틀리면, 인증 에러가 발생한다.")
        @Test
        fun throwsUnauthorized_whenPrevPwIsWrong() {
            // arrange
            val user = createUser()
            userService.signup(user)

            val command = createChangePwCommand(
                loginId = "testuser01",
                loginPw = "password1234",
                prevPw = "wrongPrevPw12!",
                nextPw = "newPassword1!",
            )

            // act
            val exception = assertThrows<CoreException> {
                userFacade.changePw(command)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }
    }
}
