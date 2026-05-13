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
class UserServiceIntegrationTest @Autowired constructor(
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

    @DisplayName("signup을 호출할 때,")
    @Nested
    inner class Signup {

        @DisplayName("유효한 정보로 회원가입하면, 성공적으로 저장된다.")
        @Test
        fun savesUser_whenValidInfoIsProvided() {
            // arrange
            val user = createUser()

            // act & assert (예외 없이 완료)
            userService.signup(user)
        }

        @DisplayName("같은 ID로 2번 가입하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenDuplicateLoginId() {
            // arrange
            val user = createUser(loginId = "duplicateUser")
            userService.signup(user)

            val duplicateUser = createUser(loginId = "duplicateUser")

            // act
            val exception = assertThrows<CoreException> {
                userService.signup(duplicateUser)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("비밀번호가 유효하지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenPasswordIsInvalid() {
            // act & assert — 짧은 비밀번호
            val shortPwException = assertThrows<CoreException> {
                createUser(rawPassword = "short7")
            }
            assertThat(shortPwException.errorType).isEqualTo(ErrorType.BAD_REQUEST)

            // act & assert — 한글 포함 비밀번호
            val koreanPwException = assertThrows<CoreException> {
                createUser(rawPassword = "비밀번호abcd1234")
            }
            assertThat(koreanPwException.errorType).isEqualTo(ErrorType.BAD_REQUEST)

            // act & assert — 생년월일 포함 비밀번호
            val birthPwException = assertThrows<CoreException> {
                createUser(rawPassword = "pass19950315!!")
            }
            assertThat(birthPwException.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("login을 호출할 때,")
    @Nested
    inner class Login {

        @DisplayName("회원이 존재하고 ID, PW가 일치하는 경우 User를 올바르게 반환한다.")
        @Test
        fun returnsUser_whenCredentialsAreValid() {
            // arrange
            val pw = "password1234"
            val user = createUser()
            userService.signup(user)

            // act
            val result = userService.login(user.loginId, pw)

            // assert
            assertThat(result.copy(id = 0)).isEqualTo(user)
        }

        @DisplayName("로그인 정보가 올바르지 않은 경우 인증 에러가 발생한다.")
        @Test
        fun throwsUnauthorized_whenPasswordIsWrong() {
            // arrange
            val wrongPasswd = "wrongPassword1!"
            val user = createUser()
            userService.signup(user)

            // act
            val exception = assertThrows<CoreException> {
                userService.login(user.loginId, wrongPasswd)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }

        @DisplayName("loginId가 빈 값이면, 인증 에러가 발생한다.")
        @Test
        fun throwsUnauthorized_whenLoginIdIsEmpty() {
            // arrange
            val user = createUser()
            userService.signup(user)

            // act
            val exception = assertThrows<CoreException> {
                userService.login("", "password1234")
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }

        @DisplayName("password가 빈 값이면, 인증 에러가 발생한다.")
        @Test
        fun throwsUnauthorized_whenPasswordIsEmpty() {
            // arrange
            val user = createUser()
            userService.signup(user)

            // act
            val exception = assertThrows<CoreException> {
                userService.login(user.loginId, "")
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }
    }
}
