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

class NameTest {

    @DisplayName("Name 생성 시,")
    @Nested
    inner class Create {

        @DisplayName("유효한 이름이면, 정상적으로 생성된다.")
        @Test
        fun createsName_whenValidValueIsProvided() {
            // act & assert
            assertDoesNotThrow { Name("홍길동") }
        }

        @DisplayName("이름이 빈값이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenNameIsBlank() {
            // act
            val result = assertThrows<CoreException> { Name("") }

            // assert
            assertThat(result.message).isEqualTo("이름은 비어있을 수 없습니다.")
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("이름이 2자 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenNameIsTooShort() {
            // act
            val result = assertThrows<CoreException> { Name("홍") }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("이름이 20자 초과이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenNameIsTooLong() {
            // act
            val result = assertThrows<CoreException> { Name("홍".repeat(21)) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("이름에 숫자나 특수문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["만식90", "만식@"])
        fun throwsBadRequest_whenNameContainsInvalidChar(invalidName: String) {
            // act
            val result = assertThrows<CoreException> { Name(invalidName) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("마스킹 시,")
    @Nested
    inner class Masked {

        @DisplayName("이름의 마지막 글자가 * 로 마스킹되어 반환된다.")
        @Test
        fun returnsMaskedName_whenCalled() {
            // arrange
            val name = Name("홍길동")

            // act & assert
            assertThat(name.masked()).isEqualTo("홍길*")
        }

        @DisplayName("두 글자 이름도 마지막 글자가 * 로 마스킹되어 반환된다.")
        @Test
        fun returnsMaskedName_whenNameHasTwoChars() {
            // arrange
            val name = Name("홍길")

            // act & assert
            assertThat(name.masked()).isEqualTo("홍*")
        }
    }
}
