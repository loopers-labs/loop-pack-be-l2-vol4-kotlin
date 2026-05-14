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
        @DisplayName("유효한 정보로 가입하면 회원이 저장된다.")
        @Test
        fun signUp_whenAllFieldsAreValid() {
            // arrange
            val loginId = "seondays"
            val encodedPassword = EncodedPassword("encodedPassword")
            val name = "선데이"
            val birthDate = LocalDate.of(1990, 1, 1)
            val email = "seondays@example.com"

            // act
            val result = userService.signUp(loginId, encodedPassword, name, birthDate, email)

            // assert
            assertAll(
                { assertThat(result.loginId).isEqualTo(loginId) },
                { assertThat(result.name).isEqualTo(name) },
                { assertThat(result.email).isEqualTo(email) },
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
                    encodedPassword = EncodedPassword("encodedPassword"),
                    name = "선데이",
                    birthDate = LocalDate.of(1990, 1, 1),
                    email = "seondays@example.com",
                ),
            )

            // act & assert
            val result = assertThrows<CoreException> {
                userService.signUp(
                    loginId = "seondays",
                    encodedPassword = EncodedPassword("encodedPassword"),
                    name = "다른이름",
                    birthDate = LocalDate.of(1990, 1, 1),
                    email = "other@example.com",
                )
            }
            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
        }
    }
}
