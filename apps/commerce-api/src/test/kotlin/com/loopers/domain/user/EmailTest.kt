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

class EmailTest {

    @DisplayName("Email 생성 시,")
    @Nested
    inner class Create {

        @DisplayName("유효한 이메일 형식이면, 정상적으로 생성된다.")
        @Test
        fun createsEmail_whenValidValueIsProvided() {
            // act & assert
            assertDoesNotThrow { Email("test@test.com") }
        }

        @DisplayName("이메일이 빈값이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenEmailIsBlank() {
            // act
            val result = assertThrows<CoreException> { Email("") }

            // assert
            assertThat(result.message).isEqualTo("이메일은 필수입니다.")
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("이메일 형식이 올바르지 않으면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["test", "test@", "@test.com", "test@test"])
        fun throwsBadRequest_whenEmailIsInvalidFormat(invalidEmail: String) {
            // act
            val result = assertThrows<CoreException> { Email(invalidEmail) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}
