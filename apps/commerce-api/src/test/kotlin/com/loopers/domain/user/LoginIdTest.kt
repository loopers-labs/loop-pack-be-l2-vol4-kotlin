package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class LoginIdTest {

    @DisplayName("LoginId 생성 시,")
    @Nested
    inner class Create {

        @DisplayName("영문과 숫자로 구성된 loginId이면, 정상적으로 생성된다.")
        @Test
        fun createsLoginId_whenValidValueIsProvided() {
            // act & assert
            assertDoesNotThrow { LoginId("user01") }
        }

        @DisplayName("loginId가 빈값이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenLoginIdIsBlank() {
            // act
            val result = assertThrows<CoreException> { LoginId("") }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("loginId에 영문, 숫자 외 문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["user@1", "user 1", "유저01", "user!1"])
        fun throwsBadRequest_whenLoginIdContainsInvalidChar(invalidLoginId: String) {
            // act
            val result = assertThrows<CoreException> { LoginId(invalidLoginId) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}
