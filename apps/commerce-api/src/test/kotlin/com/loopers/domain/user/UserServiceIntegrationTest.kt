package com.loopers.domain.user

import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate

@SpringBootTest
class UserServiceIntegrationTest @Autowired constructor(
    private val userService: UserService,
    private val userJpaRepository: UserJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("회원가입 시, ")
    @Nested
    inner class SignUp {
        @DisplayName("유효한 정보로 가입하면 회원이 저장되며 비밀번호는 BCrypt 형식으로 저장된다.")
        @Test
        fun signUp_whenAllFieldsAreValid() {
            // arrange
            val loginId = "seondays"
            val rawPassword = "Password1!"
            val name = "선데이"
            val birthDate = LocalDate.of(1990, 1, 1)
            val email = "seondays@example.com"

            // act
            val result = userService.signUp(loginId, rawPassword, name, birthDate, email)

            // assert
            assertAll(
                { assertThat(result.loginId).isEqualTo(loginId) },
                { assertThat(result.name).isEqualTo(name) },
                { assertThat(result.email).isEqualTo(email) },
                { assertThat(result.password).startsWith("\$2") },
                { assertThat(userJpaRepository.findByLoginId(loginId)).isNotNull() },
            )
        }

        @DisplayName("이미 존재하는 로그인 ID로 가입하면 CONFLICT 예외가 발생한다.")
        @Test
        fun throwsConflict_whenLoginIdAlreadyExists() {
            // arrange
            userJpaRepository.save(
                UserModel(
                    loginId = "seondays",
                    encodedPassword = EncodedPassword("\$2a\$10\$existingHashedPassword."),
                    name = "선데이",
                    birthDate = LocalDate.of(1990, 1, 1),
                    email = "seondays@example.com",
                ),
            )

            // act & assert
            val result = assertThrows<CoreException> {
                userService.signUp(
                    loginId = "seondays",
                    rawPassword = "Password1!",
                    name = "다른이름",
                    birthDate = LocalDate.of(1990, 1, 1),
                    email = "other@example.com",
                )
            }
            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
        }
    }

    @DisplayName("내 정보 조회 시, ")
    @Nested
    inner class GetMe {
        @DisplayName("올바른 로그인 ID 와 비밀번호로 조회하면 정상적인 결과를 반환한다.")
        @Test
        fun getMe_whenCredentialsAreValid() {
            // arrange
            val loginId = "seondays"
            val rawPassword = "Password1!"
            userService.signUp(
                loginId = loginId,
                rawPassword = rawPassword,
                name = "선데이",
                birthDate = LocalDate.of(1990, 1, 1),
                email = "seondays@example.com",
            )

            // act
            val result = userService.getUserInfo(loginId, rawPassword)

            // assert
            assertAll(
                { assertThat(result.loginId).isEqualTo(loginId) },
                { assertThat(result.name).isEqualTo("선데이") },
                { assertThat(result.email).isEqualTo("seondays@example.com") },
            )
        }

        @DisplayName("존재하지 않는 로그인 ID 로 조회하면 UNAUTHORIZED 예외가 발생한다.")
        @Test
        fun throwsUnauthorized_whenLoginIdNotFound() {
            // act & assert
            val result = assertThrows<CoreException> {
                userService.getUserInfo(loginId = "nonexistent", rawPassword = "Password1!")
            }
            assertThat(result.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }

        @DisplayName("비밀번호가 일치하지 않으면 UNAUTHORIZED 예외가 발생한다.")
        @Test
        fun throwsUnauthorized_whenPasswordDoesNotMatch() {
            // arrange
            val loginId = "seondays"
            userService.signUp(
                loginId = loginId,
                rawPassword = "Password1!",
                name = "선데이",
                birthDate = LocalDate.of(1990, 1, 1),
                email = "seondays@example.com",
            )

            // act & assert
            val result = assertThrows<CoreException> {
                userService.getUserInfo(loginId = loginId, rawPassword = "WrongPass1!")
            }
            assertThat(result.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }
    }

    @DisplayName("비밀번호 수정 시, ")
    @Nested
    inner class ChangePassword {
        @DisplayName("올바른 현재 비밀번호와 유효한 새 비밀번호로 변경하면 정상적으로 수정된다.")
        @Test
        fun changePassword_whenCredentialsAreValid() {
            // arrange
            val loginId = "seondays"
            val oldPassword = "OldPass1!"
            val newPassword = "NewPass1!"
            userService.signUp(loginId, oldPassword, "선데이", LocalDate.of(1990, 1, 1), "seondays@example.com")

            // act
            userService.changePassword(loginId, oldPassword, newPassword)

            // assert
            assertAll(
                { assertThat(userService.getUserInfo(loginId, newPassword).loginId).isEqualTo(loginId) },
                {
                    assertThrows<CoreException> { userService.getUserInfo(loginId, oldPassword) }
                    .also { assertThat(it.errorType).isEqualTo(ErrorType.UNAUTHORIZED) }
                },
            )
        }

        @DisplayName("존재하지 않는 로그인 ID 로 요청하면 401 Unauthorized 예외가 발생한다.")
        @Test
        fun throwsUnauthorized_whenLoginIdNotFound() {
            // act & assert
            val result = assertThrows<CoreException> {
                userService.changePassword("nonexistent", "OldPass1!", "NewPass1!")
            }
            assertThat(result.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }

        @DisplayName("현재 비밀번호가 일치하지 않으면 401 Unauthorized 예외가 발생한다.")
        @Test
        fun throwsUnauthorized_whenCurrentPasswordDoesNotMatch() {
            // arrange
            userService.signUp("seondays", "OldPass1!", "선데이", LocalDate.of(1990, 1, 1), "seondays@example.com")

            // act & assert
            val result = assertThrows<CoreException> {
                userService.changePassword("seondays", "WrongPass1!", "NewPass1!")
            }
            assertThat(result.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 동일하면 400 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenNewPasswordIsSameAsCurrent() {
            // arrange
            val password = "OldPass1!"
            userService.signUp("seondays", password, "선데이", LocalDate.of(1990, 1, 1), "seondays@example.com")

            // act & assert
            val result = assertThrows<CoreException> {
                userService.changePassword("seondays", password, password)
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("새 비밀번호에 생년월일이 포함되면 400 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenNewPasswordContainsBirthDate() {
            // arrange
            userService.signUp("seondays", "OldPass1!", "선데이", LocalDate.of(1990, 1, 1), "seondays@example.com")

            // act & assert
            val result = assertThrows<CoreException> {
                userService.changePassword("seondays", "OldPass1!", "Pass19900101!")
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}
