package com.loopers.interfaces.support

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LoopersHeadersTest {
    @DisplayName("관리자 헤더 검증")
    @Nested
    inner class ValidateAdmin {
        @DisplayName("관리자 헤더 값이 loopers.admin 이면 통과한다")
        @Test
        fun passes_whenAdminHeaderValueIsValid() {
            LoopersHeaders.validateAdmin("loopers.admin")
        }

        @DisplayName("관리자 헤더 값이 loopers.admin 이 아니면 인증 실패 예외를 던진다")
        @Test
        fun throwsUnauthorized_whenAdminHeaderValueIsInvalid() {
            val result = assertThrows<CoreException> {
                LoopersHeaders.validateAdmin("admin")
            }

            assertThat(result.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }
    }

    @DisplayName("사용자 헤더 검증")
    @Nested
    inner class ValidateUser {
        @DisplayName("사용자 헤더 값이 모두 비어 있지 않으면 통과한다")
        @Test
        fun passes_whenUserHeadersAreNotBlank() {
            LoopersHeaders.validateUser(loginId = "loopers", password = "password")
        }

        @DisplayName("사용자 로그인 ID 헤더 값이 비어 있으면 인증 실패 예외를 던진다")
        @Test
        fun throwsUnauthorized_whenLoginIdHeaderIsBlank() {
            val result = assertThrows<CoreException> {
                LoopersHeaders.validateUser(loginId = " ", password = "password")
            }

            assertThat(result.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }

        @DisplayName("사용자 비밀번호 헤더 값이 비어 있으면 인증 실패 예외를 던진다")
        @Test
        fun throwsUnauthorized_whenPasswordHeaderIsBlank() {
            val result = assertThrows<CoreException> {
                LoopersHeaders.validateUser(loginId = "loopers", password = " ")
            }

            assertThat(result.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }
    }
}
