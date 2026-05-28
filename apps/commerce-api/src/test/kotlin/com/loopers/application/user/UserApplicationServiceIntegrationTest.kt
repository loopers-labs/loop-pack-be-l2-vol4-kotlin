package com.loopers.application.user

import com.loopers.domain.auth.AuthRepositoryPort
import com.loopers.interfaces.api.user.UserApplicationServicePort
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
class UserApplicationServiceIntegrationTest @Autowired constructor(
    private val userApplicationService: UserApplicationServicePort,
    private val authRepositoryPort: AuthRepositoryPort,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private val defaultBirth: LocalDate = LocalDate.of(1995, 3, 15)

    private fun signupCommand(
        loginId: String = "testuser01",
        rawPassword: String = "Password1!",
    ): SignupCommand = SignupCommand(
        loginId = loginId,
        rawPassword = rawPassword,
        name = "홍길동",
        birth = defaultBirth,
        email = "test@example.com",
    )

    @DisplayName("signup을 호출할 때, ")
    @Nested
    inner class Signup {
        @DisplayName("정상 가입 시, User와 Auth가 모두 저장된다.")
        @Test
        fun savesUserAndAuth_whenValid() {
            val user = userApplicationService.signup(signupCommand())

            assertThat(user.id).isGreaterThan(0L)
            val auth = authRepositoryPort.findByLoginId("testuser01")
            assertThat(auth).isNotNull
            assertThat(auth?.userId).isEqualTo(user.id)
        }

        @DisplayName("동일 loginId로 다시 가입하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenDuplicateLoginId() {
            userApplicationService.signup(signupCommand(loginId = "testuser01"))

            val result = assertThrows<CoreException> {
                userApplicationService.signup(signupCommand(loginId = "testuser01"))
            }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("getMyInfo를 호출할 때, ")
    @Nested
    inner class GetMyInfo {
        @DisplayName("올바른 자격증명이 주어지면, 사용자 정보를 반환한다.")
        @Test
        fun returnsInfo_whenCredentialsAreValid() {
            userApplicationService.signup(signupCommand(loginId = "testuser01", rawPassword = "Password1!"))

            val info = userApplicationService.getMyInfo("testuser01", "Password1!")

            assertThat(info.loginId).isEqualTo("testuser01")
            assertThat(info.name).isEqualTo("홍길동")
            assertThat(info.email).isEqualTo("test@example.com")
        }

        @DisplayName("자격증명이 틀리면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        fun throwsUnauthorized_whenCredentialsWrong() {
            userApplicationService.signup(signupCommand(loginId = "testuser01", rawPassword = "Password1!"))

            val result = assertThrows<CoreException> {
                userApplicationService.getMyInfo("testuser01", "WrongPass1!")
            }

            assertThat(result.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }
    }

    @DisplayName("changePassword를 호출할 때, ")
    @Nested
    inner class ChangePassword {
        @DisplayName("자격증명·이전 비밀번호·새 비밀번호가 모두 유효하면, 비밀번호가 변경된다.")
        @Test
        fun changesPassword_whenAllValid() {
            userApplicationService.signup(signupCommand(loginId = "testuser01", rawPassword = "Password1!"))
            val command = ChangePwCommand("testuser01", "Password1!", "Password1!", "NewPass1!!")

            userApplicationService.changePassword(command)

            val info = userApplicationService.getMyInfo("testuser01", "NewPass1!!")
            assertThat(info.loginId).isEqualTo("testuser01")
        }

        @DisplayName("자격증명(loginPw)이 틀리면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        fun throwsUnauthorized_whenLoginPwWrong() {
            userApplicationService.signup(signupCommand(loginId = "testuser01", rawPassword = "Password1!"))
            val command = ChangePwCommand("testuser01", "WrongPass1!", "Password1!", "NewPass1!!")

            val result = assertThrows<CoreException> { userApplicationService.changePassword(command) }

            assertThat(result.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }

        @DisplayName("이전 비밀번호가 틀리면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        fun throwsUnauthorized_whenPrevPwWrong() {
            userApplicationService.signup(signupCommand(loginId = "testuser01", rawPassword = "Password1!"))
            val command = ChangePwCommand("testuser01", "Password1!", "WrongPrev1!", "NewPass1!!")

            val result = assertThrows<CoreException> { userApplicationService.changePassword(command) }

            assertThat(result.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }
    }
}
