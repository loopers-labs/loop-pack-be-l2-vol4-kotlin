package com.loopers.application.user

import com.loopers.domain.user.PasswordEncoder
import com.loopers.domain.user.User
import com.loopers.domain.user.UserAccountService
import com.loopers.domain.user.UserRepository
import com.loopers.fixture.user.UserFixture
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class UserServiceTest {
    @DisplayName("회원가입")
    @Nested
    inner class SignUp {
        private val userRepository = FakeUserRepository()
        private val userService = UserService(userRepository, UserAccountService())

        @DisplayName("회원가입 정보가 유효하면 회원을 저장하고 회원 정보를 반환한다")
        @Test
        fun savesUserAndReturnsUserInfo_whenSignUpCommandIsValid() {
            val command = UserFixture.createSignUpCommand()

            val result = userService.signUp(command)

            assertThat(userRepository.users).hasSize(1)
            assertThat(result.loginId).isEqualTo(command.loginId)
        }

        @DisplayName("이미 가입된 로그인 ID 로 회원가입하면 실패한다")
        @Test
        fun throwsConflict_whenLoginIdAlreadyExists() {
            userRepository.save(UserFixture.createUser(loginId = "loopers123"))
            val command = UserFixture.createSignUpCommand(loginId = "loopers123")

            val result = assertThrows<CoreException> {
                userService.signUp(command)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
        }
    }

    @DisplayName("내 정보 조회")
    @Nested
    inner class GetMe {
        private val userRepository = FakeUserRepository()
        private val userService = UserService(userRepository, UserAccountService())

        @DisplayName("로그인 ID 와 비밀번호가 유효하면 회원 정보를 반환한다")
        @Test
        fun returnsUserInfo_whenCredentialsAreValid() {
            val rawPassword = "Loopers123!"
            userRepository.save(
                UserFixture.createUser(
                    loginId = "loopers123",
                    password = PasswordEncoder.encode(rawPassword),
                ),
            )

            val result = userService.getMe("loopers123", rawPassword)

            assertThat(result.loginId).isEqualTo("loopers123")
        }

        @DisplayName("가입되지 않은 로그인 ID 로 조회하면 실패한다")
        @Test
        fun throwsNotFound_whenLoginIdDoesNotExist() {
            val result = assertThrows<CoreException> {
                userService.getMe("loopers123", "Loopers123!")
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("비밀번호 수정")
    @Nested
    inner class UpdatePassword {
        private val userRepository = FakeUserRepository()
        private val userService = UserService(userRepository, UserAccountService())

        @DisplayName("비밀번호 수정이 성공하면 변경된 사용자를 저장한다")
        @Test
        fun savesUser_whenPasswordIsUpdated() {
            val rawPassword = "Loopers123!"
            userRepository.save(
                UserFixture.createUser(
                    loginId = "loopers123",
                    password = PasswordEncoder.encode(rawPassword),
                ),
            )

            userService.updatePassword(
                loginId = "loopers123",
                rawPassword = rawPassword,
                newRawPassword = "NewLoopers1!",
            )

            val savedUser = userRepository.findByLoginId("loopers123")
            assertThat(userRepository.users).hasSize(1)
            assertThat(PasswordEncoder.matches("NewLoopers1!", savedUser?.password.orEmpty())).isTrue()
        }
    }

    private class FakeUserRepository : UserRepository {
        val users = mutableListOf<User>()

        override fun existsByLoginId(loginId: String): Boolean {
            return users.any { it.loginId == loginId }
        }

        override fun findByLoginId(loginId: String): User? {
            return users.find { it.loginId == loginId }
        }

        override fun save(user: User): User {
            val savedUser = if (user.id == 0L) {
                User(
                    id = (users.size + 1).toLong(),
                    loginId = user.loginId,
                    password = user.password,
                    name = user.name,
                    birthDate = user.birthDate,
                    email = user.email,
                )
            } else {
                user
            }
            users.indexOfFirst { it.id == savedUser.id }
                .takeIf { it >= 0 }
                ?.let { users[it] = savedUser }
                ?: users.add(savedUser)
            return savedUser
        }
    }
}
