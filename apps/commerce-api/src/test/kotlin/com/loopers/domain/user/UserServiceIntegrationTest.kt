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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

@SpringBootTest
class UserServiceIntegrationTest @Autowired constructor(
    private val userService: UserService,
    private val userJpaRepository: UserJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val LOGIN_ID = "user01"
        private const val PASSWORD = "Password1!"
        private const val NAME = "홍길동"
        private const val BIRTH_DATE = "19900628"
        private const val EMAIL = "test@test.com"
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("회원가입 시,")
    @Nested
    inner class Register {

        @DisplayName("유효한 정보로 가입하면, DB에 정상적으로 저장된다.")
        @Test
        fun savesUser_whenValidInfoIsProvided() {
            // act
            val result = userService.register(
                loginId = LOGIN_ID,
                password = PASSWORD,
                name = NAME,
                birthDate = BIRTH_DATE,
                email = EMAIL,
            )

            // assert
            val saved = userJpaRepository.findByLoginId(LOGIN_ID)

            assertAll(
                { assertThat(saved).isNotNull() },
                { assertThat(saved?.loginId).isEqualTo(LOGIN_ID) },
                { assertThat(saved?.name).isEqualTo(NAME) },
                { assertThat(saved?.email).isEqualTo(EMAIL) },
            )
        }

        @DisplayName("유효한 정보로 가입하면, 비밀번호는 암호화되어 저장된다.")
        @Test
        fun savesEncodedPassword_whenValidInfoIsProvided() {
            // act
            userService.register(
                loginId = LOGIN_ID,
                password = PASSWORD,
                name = NAME,
                birthDate = BIRTH_DATE,
                email = EMAIL,
            )

            // assert
            val saved = userJpaRepository.findByLoginId(LOGIN_ID)
            assertAll(
                { assertThat(saved?.password).isNotEqualTo(PASSWORD) },
                { assertThat(BCryptPasswordEncoder().matches(PASSWORD, saved?.password)).isTrue() },
            )
        }

        @DisplayName("이미 가입된 loginId로 가입하면, CONFLICT 예외가 발생한다.")
        @Test
        fun throwsConflict_whenLoginIdAlreadyExists() {
            // arrange
            userService.register(
                loginId = LOGIN_ID,
                password = PASSWORD,
                name = NAME,
                birthDate = BIRTH_DATE,
                email = EMAIL,
            )

            // act
            val result = assertThrows<CoreException> {
                userService.register(
                    loginId = LOGIN_ID,
                    password = PASSWORD,
                    name = NAME,
                    birthDate = BIRTH_DATE,
                    email = EMAIL,
                )
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
        }
    }

    @DisplayName("내 정보 조회 시,")
    @Nested
    inner class GetMyInfo {

        @DisplayName("가입된 loginId로 조회하면, 유저 정보를 반환한다.")
        @Test
        fun returnsUser_whenValidLoginIdIsProvided() {
            // arrange
            userService.register(
                loginId = LOGIN_ID,
                password = PASSWORD,
                name = NAME,
                birthDate = BIRTH_DATE,
                email = EMAIL,
            )

            // act
            val result = userService.getMyInfo(LOGIN_ID)

            // assert
            assertAll(
                { assertThat(result.loginId).isEqualTo(LOGIN_ID) },
                { assertThat(result.name).isEqualTo(NAME) },
                { assertThat(result.birthDate).isEqualTo(BIRTH_DATE) },
                { assertThat(result.email).isEqualTo(EMAIL) },
            )
        }

        @DisplayName("존재하지 않는 loginId로 조회하면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenLoginIdDoesNotExist() {
            // act
            val result = assertThrows<CoreException> {
                userService.getMyInfo(LOGIN_ID)
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }
}
