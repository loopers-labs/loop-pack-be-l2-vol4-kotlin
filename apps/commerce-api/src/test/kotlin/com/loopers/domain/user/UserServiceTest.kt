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
import com.loopers.domain.user.PasswordEncryptor

class UserServiceTest {

    private val userRepository: UserRepository = mock()
    private val passwordEncryptor: PasswordEncryptor = mock()
    private val userService: UserService = UserService(userRepository, passwordEncryptor)

    companion object {
        private const val LOGIN_ID = "user01"
        private const val PASSWORD = "Password1!"
        private const val ENCODED_PASSWORD = "\$2a\$10\$hashedvalue"
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
}
