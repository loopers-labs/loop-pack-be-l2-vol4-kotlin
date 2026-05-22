package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class UserServiceTest {
    private val repository = FakeUserRepository()
    private val passwordEncoder = FakePasswordEncoder()
    private val service = UserService(repository, passwordEncoder)

    @DisplayName("register 를 호출하면, ")
    @Nested
    inner class Register {
        @DisplayName("저장된 User 가 id 와 함께 반환된다.")
        @Test
        fun persistsAndReturnsUserWithGeneratedId() {
            val saved = service.register(
                UserCommand.Register(
                    loginId = "loopers01",
                    rawPassword = RawPassword("abcd1234"),
                    name = "홍길동",
                    birthdate = LocalDate.of(1990, 1, 1),
                    email = "user@example.com",
                ),
            )

            assertAll(
                { assertThat(saved.id).isNotZero() },
                { assertThat(saved.loginId).isEqualTo("loopers01") },
                { assertThat(repository.findByLoginId("loopers01")).isNotNull() },
            )
        }

        @DisplayName("User 의 encryptedPassword 는 평문이 아닌 인코더가 만든 해시이다.")
        @Test
        fun storesEncryptedPasswordNotRaw() {
            val saved = service.register(
                UserCommand.Register(
                    loginId = "loopers01",
                    rawPassword = RawPassword("abcd1234"),
                    name = "홍길동",
                    birthdate = LocalDate.of(1990, 1, 1),
                    email = "user@example.com",
                ),
            )

            assertAll(
                { assertThat(saved.encryptedPassword).isNotEqualTo("abcd1234") },
                { assertThat(passwordEncoder.matches(RawPassword("abcd1234"), saved.encryptedPassword)).isTrue() },
            )
        }

        @DisplayName("이미 동일한 loginId 가 존재하면, CONFLICT 예외를 던진다.")
        @Test
        fun throwsConflictWhenLoginIdAlreadyExists() {
            service.register(
                UserCommand.Register(
                    loginId = "loopers01",
                    rawPassword = RawPassword("abcd1234"),
                    name = "홍길동",
                    birthdate = LocalDate.of(1990, 1, 1),
                    email = "user@example.com",
                ),
            )

            val ex = assertThrows<CoreException> {
                service.register(
                    UserCommand.Register(
                        loginId = "loopers01",
                        rawPassword = RawPassword("wxyz5678"),
                        name = "다른사람",
                        birthdate = LocalDate.of(1992, 2, 2),
                        email = "other@example.com",
                    ),
                )
            }

            assertThat(ex.errorType).isEqualTo(ErrorType.CONFLICT)
        }

        @DisplayName("password 의 숫자 그룹이 birthdate 의 yyyyMMdd 를 포함하면, BAD_REQUEST 예외를 던진다.")
        @Test
        fun rejectsPasswordContainingBirthdateAsYyyyMMdd() {
            val ex = assertThrows<CoreException> {
                service.register(
                    UserCommand.Register(
                        loginId = "loopers01",
                        rawPassword = RawPassword("ab19900101"),
                        name = "홍길동",
                        birthdate = LocalDate.of(1990, 1, 1),
                        email = "user@example.com",
                    ),
                )
            }

            assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("password 의 숫자 그룹이 birthdate 의 yyMMdd 를 포함하면, BAD_REQUEST 예외를 던진다.")
        @Test
        fun rejectsPasswordContainingBirthdateAsYyMMdd() {
            val ex = assertThrows<CoreException> {
                service.register(
                    UserCommand.Register(
                        loginId = "loopers01",
                        rawPassword = RawPassword("abc900101"),
                        name = "홍길동",
                        birthdate = LocalDate.of(1990, 1, 1),
                        email = "user@example.com",
                    ),
                )
            }

            assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("password 의 숫자 그룹이 birthdate 의 MMdd 를 포함하면, BAD_REQUEST 예외를 던진다.")
        @Test
        fun rejectsPasswordContainingBirthdateAsMMdd() {
            val ex = assertThrows<CoreException> {
                service.register(
                    UserCommand.Register(
                        loginId = "loopers01",
                        rawPassword = RawPassword("abcd0101"),
                        name = "홍길동",
                        birthdate = LocalDate.of(1990, 1, 1),
                        email = "user@example.com",
                    ),
                )
            }

            assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("authenticate 를 호출할 때, ")
    @Nested
    inner class Authenticate {
        @DisplayName("올바른 자격증명이면, 저장된 User 가 반환된다.")
        @Test
        fun returnsUserOnCorrectCredentials() {
            val registered = service.register(
                UserCommand.Register(
                    loginId = "loopers01",
                    rawPassword = RawPassword("abcd1234"),
                    name = "홍길동",
                    birthdate = LocalDate.of(1990, 1, 1),
                    email = "user@example.com",
                ),
            )

            val result = service.authenticate("loopers01", RawPassword("abcd1234"))

            assertThat(result.id).isEqualTo(registered.id)
        }

        @DisplayName("loginId 가 존재하지 않으면, UNAUTHORIZED 예외를 던진다.")
        @Test
        fun throwsUnauthorizedOnUnknownLoginId() {
            val ex = assertThrows<CoreException> {
                service.authenticate("nobody", RawPassword("abcd1234"))
            }

            assertThat(ex.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }

        @DisplayName("비밀번호가 일치하지 않으면, UNAUTHORIZED 예외를 던진다.")
        @Test
        fun throwsUnauthorizedOnWrongPassword() {
            service.register(
                UserCommand.Register(
                    loginId = "loopers01",
                    rawPassword = RawPassword("abcd1234"),
                    name = "홍길동",
                    birthdate = LocalDate.of(1990, 1, 1),
                    email = "user@example.com",
                ),
            )

            val ex = assertThrows<CoreException> {
                service.authenticate("loopers01", RawPassword("wxyz5678"))
            }

            assertThat(ex.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }
    }

    @DisplayName("changePassword 를 호출할 때, ")
    @Nested
    inner class ChangePassword {
        private fun registerDefault() = service.register(
            UserCommand.Register(
                loginId = "loopers01",
                rawPassword = RawPassword("abcd1234"),
                name = "홍길동",
                birthdate = LocalDate.of(1990, 1, 1),
                email = "user@example.com",
            ),
        )

        @DisplayName("oldPassword 가 저장된 해시와 일치하면, 새 해시로 교체된다.")
        @Test
        fun overwritesHashWhenOldPasswordMatches() {
            val user = registerDefault()

            service.changePassword(user.id, RawPassword("abcd1234"), RawPassword("wxyz5678"))

            val reloaded = repository.findById(user.id)!!
            assertAll(
                { assertThat(passwordEncoder.matches(RawPassword("wxyz5678"), reloaded.encryptedPassword)).isTrue() },
                { assertThat(passwordEncoder.matches(RawPassword("abcd1234"), reloaded.encryptedPassword)).isFalse() },
            )
        }

        @DisplayName("oldPassword 가 일치하지 않으면, BAD_REQUEST 예외를 던진다.")
        @Test
        fun throwsBadRequestWhenOldPasswordDoesNotMatch() {
            val user = registerDefault()

            val ex = assertThrows<CoreException> {
                service.changePassword(user.id, RawPassword("wrongold"), RawPassword("wxyz5678"))
            }

            assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("newPassword 가 현재 저장된 비밀번호와 동일하면, BAD_REQUEST 예외를 던진다.")
        @Test
        fun throwsBadRequestWhenNewPasswordEqualsCurrent() {
            val user = registerDefault()

            val ex = assertThrows<CoreException> {
                service.changePassword(user.id, RawPassword("abcd1234"), RawPassword("abcd1234"))
            }

            assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("newPassword 에 birthdate 숫자가 포함되면, BAD_REQUEST 예외를 던진다.")
        @Test
        fun throwsBadRequestWhenNewPasswordContainsBirthdate() {
            val user = registerDefault()

            val ex = assertThrows<CoreException> {
                service.changePassword(user.id, RawPassword("abcd1234"), RawPassword("ab19900101"))
            }

            assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}
