package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class UserServiceTest {

    private val userRepository: UserRepository = mock()
    private val passwordEncryptor: PasswordEncryptor = mock()
    private val userService: UserService = UserService(userRepository, passwordEncryptor)

    companion object {
        private const val LOGIN_ID = "user01"
        private const val PASSWORD = "Password1!"
        private const val ENCODED_PASSWORD = "\$2a\$10\$hashedvalue"
        private const val NEW_PASSWORD = "NewPass1!"
        private const val ENCODED_NEW_PASSWORD = "\$2a\$10\$newhashedvalue"
        private const val NAME = "홍길동"
        private const val BIRTH_DATE = "19900628"
        private const val EMAIL = "test@test.com"
    }

    @DisplayName("회원가입 시")
    @Nested
    inner class Join {

        @DisplayName("유효한 정보로 가입하면 정상적으로 저장된다.")
        @Test
        fun register_whenValidInfoIsProvided() {
            // arrange
            whenever(userRepository.findByLoginId(LOGIN_ID)).thenReturn(null)
            whenever(passwordEncryptor.encode(PASSWORD)).thenReturn(ENCODED_PASSWORD)
            whenever(userRepository.save(any())).thenAnswer { it.arguments[0] as User }

            // act
            val result = userService.register(
                loginId = LOGIN_ID,
                password = PASSWORD,
                name = NAME,
                birthDate = BIRTH_DATE,
                email = EMAIL,
            )

            // assert
            assertThat(result.loginId).isEqualTo(LOGIN_ID)
            assertThat(result.name).isEqualTo(NAME)
            assertThat(result.email).isEqualTo(EMAIL)

            verify(userRepository, times(1)).save(any())
            verify(userRepository, times(1)).findByLoginId(LOGIN_ID)
        }

        @DisplayName("이미 가입된 loginId로 가입하면 예외가 발생한다.")
        @Test
        fun register_whenLoginIdAlreadyExists() {
            // arrange
            val existingUser = User(loginId = LOGIN_ID, password = PASSWORD, name = NAME, birthDate = BIRTH_DATE, email = EMAIL)
            whenever(userRepository.findByLoginId(LOGIN_ID)).thenReturn(existingUser)

            // act
            val result = assertThrows<CoreException> {
                userService.register(loginId = LOGIN_ID, password = PASSWORD, name = NAME, birthDate = BIRTH_DATE, email = EMAIL)
            }

            // assert
            assertThat(result.message).isEqualTo("이미 가입된 로그인 ID 입니다.")
            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
        }

        @DisplayName("유효한 정보로 가입하면, 비밀번호는 암호화되어 저장된다.")
        @Test
        fun register_savesEncodedPassword() {
            // arrange
            whenever(userRepository.findByLoginId(LOGIN_ID)).thenReturn(null)
            whenever(passwordEncryptor.encode(PASSWORD)).thenReturn(ENCODED_PASSWORD)
            whenever(userRepository.save(any())).thenAnswer { it.arguments[0] as User }

            // act
            val result = userService.register(
                loginId = LOGIN_ID,
                password = PASSWORD,
                name = NAME,
                birthDate = BIRTH_DATE,
                email = EMAIL,
            )

            // assert
            assertThat(result.password).isNotEqualTo(PASSWORD)
            assertThat(result.password).isEqualTo(ENCODED_PASSWORD)
        }
    }

    @DisplayName("비밀번호 수정 시,")
    @Nested
    inner class ChangePassword {

        @DisplayName("유효한 새 비밀번호로 변경하면, 비밀번호가 업데이트된다.")
        @Test
        fun changesPassword_whenValidNewPasswordIsProvided() {
            // arrange
            val user = User(loginId = LOGIN_ID, password = ENCODED_PASSWORD, name = NAME, birthDate = BIRTH_DATE, email = EMAIL)
            whenever(userRepository.findByLoginId(LOGIN_ID)).thenReturn(user)
            whenever(passwordEncryptor.matches(NEW_PASSWORD, ENCODED_PASSWORD)).thenReturn(false)
            whenever(passwordEncryptor.encode(NEW_PASSWORD)).thenReturn(ENCODED_NEW_PASSWORD)

            // act
            userService.changePassword(LOGIN_ID, NEW_PASSWORD)

            // assert
            verify(userRepository, times(1)).changePassword(LOGIN_ID, ENCODED_NEW_PASSWORD)
        }

        @DisplayName("현재 비밀번호와 동일한 비밀번호로 변경하면, CONFLICT 예외가 발생한다.")
        @Test
        fun throwsConflict_whenNewPasswordIsSameAsCurrent() {
            // arrange
            val user = User(loginId = LOGIN_ID, password = ENCODED_PASSWORD, name = NAME, birthDate = BIRTH_DATE, email = EMAIL)
            whenever(userRepository.findByLoginId(LOGIN_ID)).thenReturn(user)
            whenever(passwordEncryptor.matches(PASSWORD, ENCODED_PASSWORD)).thenReturn(true)

            // act
            val result = assertThrows<CoreException> {
                userService.changePassword(LOGIN_ID, PASSWORD)
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
        }
    }

    @DisplayName("내 정보 조회 시,")
    @Nested
    inner class GetMyInfo {

        @DisplayName("존재하는 loginId가 주어지면, 유저 정보를 반환한다.")
        @Test
        fun returnsUser_whenValidLoginIdIsProvided() {
            // arrange
            val user = User(loginId = LOGIN_ID, password = ENCODED_PASSWORD, name = NAME, birthDate = BIRTH_DATE, email = EMAIL)
            whenever(userRepository.findByLoginId(LOGIN_ID)).thenReturn(user)

            // act
            val result = userService.getMyInfo(LOGIN_ID)

            // assert
            assertThat(result.loginId).isEqualTo(LOGIN_ID)
            assertThat(result.name).isEqualTo(NAME)
            assertThat(result.birthDate).isEqualTo(BIRTH_DATE)
            assertThat(result.email).isEqualTo(EMAIL)
        }

        @DisplayName("존재하지 않는 loginId가 주어지면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenLoginIdDoesNotExist() {
            // arrange
            whenever(userRepository.findByLoginId(LOGIN_ID)).thenReturn(null)

            // act
            val result = assertThrows<CoreException> {
                userService.getMyInfo(LOGIN_ID)
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }
}
