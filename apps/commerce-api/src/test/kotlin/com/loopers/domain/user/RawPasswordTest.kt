package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class RawPasswordTest {
    private val validBirthDate = LocalDate.of(1990, 1, 1)

    @DisplayName("비밀번호 생성 시 길이가, ")
    @Nested
    inner class LengthValidation {
        @DisplayName("8자 미만이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenPasswordIsTooShort() {
            val result = assertThrows<CoreException> {
                RawPassword("Aa1!Aa1", validBirthDate)
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("16자를 초과하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenPasswordIsTooLong() {
            val result = assertThrows<CoreException> {
                RawPassword("Aa1!Aa1!Aa1!Aa1!A", validBirthDate)
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("비밀번호 생성 시 허용되지 않는 문자인, ")
    @Nested
    inner class CharacterValidation {
        @DisplayName("공백이 포함되면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenPasswordContainsSpace() {
            val result = assertThrows<CoreException> {
                RawPassword("Aa1! Aa1!", validBirthDate)
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("한글이 포함되면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenPasswordContainsKorean() {
            val result = assertThrows<CoreException> {
                RawPassword("비번Aa1!Aa1", validBirthDate)
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("비밀번호 생성 시 생년월일이, ")
    @Nested
    inner class BirthDateTokenValidation {
        @DisplayName("YYYYMMDD 형식으로 포함되면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenPasswordContainsYyyymmdd() {
            val result = assertThrows<CoreException> {
                RawPassword("pw19900101!", validBirthDate)
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("YYMMDD 형식으로 포함되면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenPasswordContainsYymmdd() {
            val result = assertThrows<CoreException> {
                RawPassword("pw900101!a", validBirthDate)
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("MMDD 형식으로 포함되면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenPasswordContainsMmdd() {
            val result = assertThrows<CoreException> {
                RawPassword("abcd0101!", validBirthDate)
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("YYYY 형식으로 포함되면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenPasswordContainsYyyy() {
            val result = assertThrows<CoreException> {
                RawPassword("abc1990!a", validBirthDate)
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("모든 규칙을 만족하는 비밀번호는 정상적으로 생성된다.")
    @Test
    fun create_whenPasswordIsValid() {
        val rawPassword = RawPassword("Aqieobcd!2514", validBirthDate)
        assertThat(rawPassword.value).isEqualTo("Aqieobcd!2514")
    }
}
