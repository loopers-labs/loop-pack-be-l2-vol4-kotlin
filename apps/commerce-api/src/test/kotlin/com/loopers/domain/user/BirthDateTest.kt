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

class BirthDateTest {

    @DisplayName("BirthDate 생성 시,")
    @Nested
    inner class Create {

        @DisplayName("유효한 yyyyMMdd 형식이면, 정상적으로 생성된다.")
        @Test
        fun createsBirthDate_whenValidFormatIsProvided() {
            // act & assert
            assertDoesNotThrow { BirthDate("19900628") }
        }

        @DisplayName("생년월일이 빈값이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenBirthDateIsBlank() {
            // act
            val result = assertThrows<CoreException> { BirthDate("") }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("생년월일이 yyyyMMdd 형식이 아니면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["1990-06-28", "900628", "19906281", "abcdefgh"])
        fun throwsBadRequest_whenBirthDateIsInvalidFormat(invalidBirthDate: String) {
            // act
            val result = assertThrows<CoreException> { BirthDate(invalidBirthDate) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}
