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
}
