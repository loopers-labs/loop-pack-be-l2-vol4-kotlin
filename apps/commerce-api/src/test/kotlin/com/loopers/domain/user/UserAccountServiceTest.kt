package com.loopers.domain.user

import com.loopers.domain.user.service.UserAccountService
import com.loopers.fixture.user.UserFixture
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class UserAccountServiceTest {
    @DisplayName("회원가입")
    @Nested
    inner class SignUp {
        private val userAccountService = UserAccountService()

        @DisplayName("회원가입 정보가 유효하면 비밀번호를 암호화한 사용자를 생성한다")
        @Test
        fun createsUserWithEncodedPassword_whenSignUpCommandIsValid() {
            val rawPassword = "Loopers123!"
            val command = UserFixture.createSignUpCommand(rawPassword = rawPassword)

            val user = userAccountService.signUp(command, loginIdTaken = false)

            assertThat(user.password).isNotEqualTo(rawPassword)
            assertThat(PasswordEncoder.matches(rawPassword, user.password)).isTrue()
        }

        @DisplayName("이미 가입된 로그인 ID 로 회원가입하면 실패한다")
        @Test
        fun throwsConflict_whenLoginIdAlreadyExists() {
            val command = UserFixture.createSignUpCommand(loginId = "loopers123")

            val result = assertThrows<CoreException> {
                userAccountService.signUp(command, loginIdTaken = true)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
        }

        @DisplayName("비밀번호 정책을 만족하지 않으면 실패한다")
        @Test
        fun throwsBadRequest_whenPasswordDoesNotSatisfyPolicy() {
            val command = UserFixture.createSignUpCommand(rawPassword = "short")

            val result = assertThrows<CoreException> {
                userAccountService.signUp(command, loginIdTaken = false)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("인증")
    @Nested
    inner class Authenticate {
        private val userAccountService = UserAccountService()

        @DisplayName("비밀번호가 유효하면 회원을 반환한다")
        @Test
        fun returnsUser_whenPasswordIsValid() {
            val rawPassword = "Loopers123!"
            val user = UserFixture.createUser(password = PasswordEncoder.encode(rawPassword))

            val result = userAccountService.authenticate(user, rawPassword)

            assertThat(result).isEqualTo(user)
        }

        @DisplayName("비밀번호가 일치하지 않으면 실패한다")
        @Test
        fun throwsUnauthorized_whenPasswordDoesNotMatch() {
            val user = UserFixture.createUser(password = PasswordEncoder.encode("Loopers123!"))

            val result = assertThrows<CoreException> {
                userAccountService.authenticate(user, "Wrong123!")
            }

            assertThat(result.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }
    }

    @DisplayName("비밀번호 수정")
    @Nested
    inner class UpdatePassword {
        private val userAccountService = UserAccountService()

        @DisplayName("기존 비밀번호가 일치하고 새 비밀번호가 유효하면 비밀번호를 변경한다")
        @Test
        fun updatesPassword_whenCurrentPasswordMatchesAndNewPasswordIsValid() {
            val currentPassword = "Loopers123!"
            val newPassword = "NewLoopers1!"
            val user = UserFixture.createUser(password = PasswordEncoder.encode(currentPassword))

            userAccountService.updatePassword(
                user = user,
                rawPassword = currentPassword,
                newRawPassword = newPassword,
            )

            assertThat(PasswordEncoder.matches(newPassword, user.password)).isTrue()
        }

        @DisplayName("기존 비밀번호가 일치하지 않으면 실패한다")
        @Test
        fun throwsUnauthorized_whenCurrentPasswordDoesNotMatch() {
            val user = UserFixture.createUser(password = PasswordEncoder.encode("Loopers123!"))

            val result = assertThrows<CoreException> {
                userAccountService.updatePassword(
                    user = user,
                    rawPassword = "Wrong123!",
                    newRawPassword = "NewLoopers1!",
                )
            }

            assertThat(result.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }

        @DisplayName("새 비밀번호가 정책을 만족하지 않으면 실패한다")
        @Test
        fun throwsBadRequest_whenNewPasswordDoesNotSatisfyPolicy() {
            val user = UserFixture.createUser(password = PasswordEncoder.encode("Loopers123!"))

            val result = assertThrows<CoreException> {
                userAccountService.updatePassword(
                    user = user,
                    rawPassword = "Loopers123!",
                    newRawPassword = "short",
                )
            }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("현재 비밀번호와 같은 비밀번호로 변경하면 실패한다")
        @Test
        fun throwsBadRequest_whenNewPasswordIsSameAsCurrentPassword() {
            val currentPassword = "Loopers123!"
            val user = UserFixture.createUser(password = PasswordEncoder.encode(currentPassword))

            val result = assertThrows<CoreException> {
                userAccountService.updatePassword(
                    user = user,
                    rawPassword = currentPassword,
                    newRawPassword = currentPassword,
                )
            }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}
