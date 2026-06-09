package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDate

class PasswordPolicyTest {
    @DisplayName("비밀번호 유효성 검증")
    @Nested
    inner class Validate {
        private val defaultBirthDate: LocalDate = LocalDate.of(1988, 2, 1)

        @DisplayName("패스워드 정책을 모두 만족하면 성공")
        @Test
        fun validates_whenPasswordSatisfiesPolicy() {
            val rawPassword = "Loopers123!"

            assertDoesNotThrow {
                PasswordPolicy.validate(
                    rawPassword = rawPassword,
                    birthDate = defaultBirthDate,
                )
            }
        }

        @DisplayName("비밀번호가 8자리 미만 16자리 초과면 실패")
        @ParameterizedTest
        @ValueSource(strings = ["pass", "12345678,12345678"])
        fun throwsBadRequest_whenPasswordLengthIsInvalid(rawPassword: String) {
            val result = assertThrows<CoreException> {
                PasswordPolicy.validate(
                    rawPassword = rawPassword,
                    birthDate = defaultBirthDate,
                )
            }

            assertEquals(ErrorType.BAD_REQUEST, result.errorType)
        }

        @DisplayName("비밀번호가 영문 대소문자, 숫자, 특수문자가 아닐경우 실패")
        @Test
        fun throwsBadRequest_whenPasswordContainsUnSupportedChars() {
            val rawPassword = "패스워드1234"
            val result = assertThrows<CoreException> {
                PasswordPolicy.validate(
                    rawPassword = rawPassword,
                    birthDate = defaultBirthDate,
                )
            }

            assertEquals(ErrorType.BAD_REQUEST, result.errorType)
        }

        @DisplayName("비밀번호에 생년월일이 포함되면 실패")
        @ParameterizedTest
        @ValueSource(strings = ["p880201p!", "p19880201"])
        fun throwsBadRequest_whenPasswordContainsBirthDate(rawPassword: String) {
            val result = assertThrows<CoreException> {
                PasswordPolicy.validate(
                    rawPassword = rawPassword,
                    birthDate = defaultBirthDate,
                )
            }

            assertEquals(ErrorType.BAD_REQUEST, result.errorType)
        }
    }
}
