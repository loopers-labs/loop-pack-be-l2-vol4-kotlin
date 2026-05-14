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

@SpringBootTest
class UserServiceIntegrationTest @Autowired constructor(
    private val userService: UserService,
    private val userJpaRepository: UserJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val LOGIN_ID = "testUser01"
        private const val RAW_PASSWORD = "Pass!234"
        private const val NAME = "홍길동"
        private const val BIRTH_DATE = "19900101"
        private const val EMAIL = "test@example.com"
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("회원가입 시,")
    @Nested
    inner class Register {
        @DisplayName("유효한 정보로 가입하면, 유저가 저장되고 비밀번호는 암호화된다.")
        @Test
        fun savesUserWithEncodedPassword_whenValidInfoIsProvided() {
            // act
            val result = userService.register(LOGIN_ID, RAW_PASSWORD, NAME, BIRTH_DATE, EMAIL)

            // assert
            assertAll(
                { assertThat(result.id).isGreaterThan(0) },
                { assertThat(result.loginId).isEqualTo(LOGIN_ID) },
                { assertThat(result.name).isEqualTo(NAME) },
                { assertThat(result.birthDate).isEqualTo(BIRTH_DATE) },
                { assertThat(result.email).isEqualTo(EMAIL) },
                { assertThat(result.encodedPassword).isNotEqualTo(RAW_PASSWORD) },
            )
        }

        @DisplayName("이미 존재하는 로그인 ID로 가입하면, CONFLICT 예외가 발생한다.")
        @Test
        fun throwsConflict_whenLoginIdAlreadyExists() {
            // arrange
            userService.register(LOGIN_ID, RAW_PASSWORD, NAME, BIRTH_DATE, EMAIL)

            // act
            val exception = assertThrows<CoreException> {
                userService.register(LOGIN_ID, RAW_PASSWORD, NAME, BIRTH_DATE, EMAIL)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.CONFLICT)
        }

        @DisplayName("비밀번호가 8자 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenPasswordIsTooShort() {
            // arrange
            val tooShort = "Ab1!567" // 7자

            // act
            val exception = assertThrows<CoreException> {
                userService.register(LOGIN_ID, tooShort, NAME, BIRTH_DATE, EMAIL)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("비밀번호가 16자를 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenPasswordIsTooLong() {
            // arrange
            val tooLong = "Ab1!5678901234567" // 17자

            // act
            val exception = assertThrows<CoreException> {
                userService.register(LOGIN_ID, tooLong, NAME, BIRTH_DATE, EMAIL)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("비밀번호에 한글이나 공백이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenPasswordContainsInvalidCharacters() {
            // arrange
            val invalidPassword = "패스워드1234!!" // 한글 포함

            // act
            val exception = assertThrows<CoreException> {
                userService.register(LOGIN_ID, invalidPassword, NAME, BIRTH_DATE, EMAIL)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("비밀번호에 생년월일(yyyyMMdd)이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenPasswordContainsBirthDate() {
            // arrange
            val passwordWithBirthDate = "Ab!19900101" // "19900101" 포함

            // act
            val exception = assertThrows<CoreException> {
                userService.register(LOGIN_ID, passwordWithBirthDate, NAME, BIRTH_DATE, EMAIL)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("내 정보 조회 시,")
    @Nested
    inner class GetByLoginId {
        @DisplayName("존재하는 loginId로 조회하면, 유저를 반환한다.")
        @Test
        fun returnsUser_whenLoginIdExists() {
            // arrange
            userService.register(LOGIN_ID, RAW_PASSWORD, NAME, BIRTH_DATE, EMAIL)

            // act
            val result = userService.getByLoginId(LOGIN_ID)

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
            val exception = assertThrows<CoreException> {
                userService.getByLoginId("nonexistent")
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("인증 시,")
    @Nested
    inner class Authenticate {
        @DisplayName("존재하는 loginId 와 올바른 비밀번호로 인증하면, 유저를 반환한다.")
        @Test
        fun returnsUser_whenValidCredentialsAreProvided() {
            // arrange
            userService.register(LOGIN_ID, RAW_PASSWORD, NAME, BIRTH_DATE, EMAIL)

            // act
            val result = userService.authenticate(LOGIN_ID, RAW_PASSWORD)

            // assert
            assertAll(
                { assertThat(result.loginId).isEqualTo(LOGIN_ID) },
                { assertThat(result.name).isEqualTo(NAME) },
            )
        }

        @DisplayName("존재하지 않는 loginId 로 인증하면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenLoginIdDoesNotExist() {
            // act
            val exception = assertThrows<CoreException> {
                userService.authenticate("nonexistent", RAW_PASSWORD)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("비밀번호가 일치하지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenPasswordDoesNotMatch() {
            // arrange
            userService.register(LOGIN_ID, RAW_PASSWORD, NAME, BIRTH_DATE, EMAIL)

            // act
            val exception = assertThrows<CoreException> {
                userService.authenticate(LOGIN_ID, "WrongPass!1")
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("비밀번호 변경 시,")
    @Nested
    inner class ChangePassword {
        @DisplayName("유효한 현재 비밀번호와 새 비밀번호로 변경하면, 비밀번호가 변경된다.")
        @Test
        fun changesPassword_whenCurrentPasswordMatchesAndNewPasswordIsValid() {
            // arrange
            val user = userService.register(LOGIN_ID, RAW_PASSWORD, NAME, BIRTH_DATE, EMAIL)
            val newPassword = "NewPass!9"

            // act
            val result = userService.changePassword(user.id, RAW_PASSWORD, newPassword)

            // assert
            assertAll(
                { assertThat(result.id).isEqualTo(user.id) },
                { assertThat(result.encodedPassword).isNotEqualTo(user.encodedPassword) },
            )
        }

        @DisplayName("존재하지 않는 유저 ID이면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenUserDoesNotExist() {
            // act
            val exception = assertThrows<CoreException> {
                userService.changePassword(999L, RAW_PASSWORD, "NewPass!9")
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("현재 비밀번호가 일치하지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenCurrentPasswordDoesNotMatch() {
            // arrange
            val user = userService.register(LOGIN_ID, RAW_PASSWORD, NAME, BIRTH_DATE, EMAIL)

            // act
            val exception = assertThrows<CoreException> {
                userService.changePassword(user.id, "WrongPass!1", "NewPass!9")
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 동일하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenNewPasswordIsSameAsCurrentPassword() {
            // arrange
            val user = userService.register(LOGIN_ID, RAW_PASSWORD, NAME, BIRTH_DATE, EMAIL)

            // act
            val exception = assertThrows<CoreException> {
                userService.changePassword(user.id, RAW_PASSWORD, RAW_PASSWORD)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("새 비밀번호에 생년월일이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenNewPasswordContainsBirthDate() {
            // arrange
            val user = userService.register(LOGIN_ID, RAW_PASSWORD, NAME, BIRTH_DATE, EMAIL)

            // act
            val exception = assertThrows<CoreException> {
                userService.changePassword(user.id, RAW_PASSWORD, "Ab!19900101")
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("새 비밀번호가 8자 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenNewPasswordIsTooShort() {
            // arrange
            val user = userService.register(LOGIN_ID, RAW_PASSWORD, NAME, BIRTH_DATE, EMAIL)

            // act
            val exception = assertThrows<CoreException> {
                userService.changePassword(user.id, RAW_PASSWORD, "Ab1!567")
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}
