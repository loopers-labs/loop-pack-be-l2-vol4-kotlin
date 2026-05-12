package com.loopers.application.user

import com.loopers.domain.user.User
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class UserServiceUnitTest {
    private val userRepository: UserRepository = mockk()
    private val userService = UserService(userRepository)

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

        @DisplayName("UserRepository의 findByLoginId가 실행된다.")
        @Test
        fun callsFindByLoginId() {
            // arrange
            val user = createUser()
            every { userRepository.findByLoginId(user.loginId) } throws CoreException(ErrorType.NOT_FOUND)
            every { userRepository.save(user) } returns user

            // act
            userService.signup(user)

            // assert
            verify { userRepository.findByLoginId(user.loginId) }
        }

        @DisplayName("findByLoginId가 유저를 찾으면, BAD_REQUEST 에러가 발생한다.")
        @Test
        fun throwsBadRequest_whenLoginIdAlreadyExists() {
            // arrange
            val user = createUser()
            every { userRepository.findByLoginId(user.loginId) } returns user

            // act
            val exception = assertThrows<CoreException> {
                userService.signup(user)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("UserRepository의 save가 실행된다.")
        @Test
        fun callsSave() {
            // arrange
            val user = createUser()
            every { userRepository.findByLoginId(user.loginId) } throws CoreException(ErrorType.NOT_FOUND)
            every { userRepository.save(user) } returns user

            // act
            userService.signup(user)

            // assert
            verify { userRepository.save(user) }
        }
    }
}
