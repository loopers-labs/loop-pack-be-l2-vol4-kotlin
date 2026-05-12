package com.loopers.domain.auth

import com.loopers.domain.user.PasswordEncryptor
import com.loopers.domain.user.User
import com.loopers.domain.user.UserRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

class AuthServiceTest {

    private val userRepository: UserRepository = mock()
    private val passwordEncryptor: PasswordEncryptor = mock()
    private val authService: AuthService = AuthService(userRepository, passwordEncryptor)

    companion object {
        private const val LOGIN_ID = "user01"
        private const val PASSWORD = "Password1!"
        private const val ENCODED_PASSWORD = "\$2a\$10\$hashedvalue"
        private const val NAME = "홍길동"
        private const val BIRTH_DATE = "19900628"
        private const val EMAIL = "test@test.com"
    }

    @DisplayName("인증 시,")
    @Nested
    inner class Authenticate {

        @DisplayName("존재하지 않는 loginId이면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenLoginIdDoesNotExist() {
            // arrange
            whenever(userRepository.findByLoginId(LOGIN_ID)).thenReturn(null)

            // act
            val result = assertThrows<CoreException> {
                authService.authenticate(LOGIN_ID, PASSWORD)
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("비밀번호가 일치하지 않으면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        fun throwsUnauthorized_whenPasswordDoesNotMatch() {
            // arrange
            val user = User(loginId = LOGIN_ID, password = ENCODED_PASSWORD, name = NAME, birthDate = BIRTH_DATE, email = EMAIL)
            whenever(userRepository.findByLoginId(LOGIN_ID)).thenReturn(user)
            whenever(passwordEncryptor.matches(PASSWORD, ENCODED_PASSWORD)).thenReturn(false)

            // act
            val result = assertThrows<CoreException> {
                authService.authenticate(LOGIN_ID, PASSWORD)
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }

        @DisplayName("유효한 loginId와 비밀번호가 주어지면, 인증된 User를 반환한다.")
        @Test
        fun returnsUser_whenValidCredentialsAreProvided() {
            // arrange
            val user = User(loginId = LOGIN_ID, password = ENCODED_PASSWORD, name = NAME, birthDate = BIRTH_DATE, email = EMAIL)
            whenever(userRepository.findByLoginId(LOGIN_ID)).thenReturn(user)
            whenever(passwordEncryptor.matches(PASSWORD, ENCODED_PASSWORD)).thenReturn(true)

            // act
            val result = authService.authenticate(LOGIN_ID, PASSWORD)

            // assert
            assertThat(result.loginId).isEqualTo(LOGIN_ID)
        }
    }
}