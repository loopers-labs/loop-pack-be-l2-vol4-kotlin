package com.loopers.domain.account.validator

import com.loopers.support.error.BadRequestException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test

class PasswordValidatorTest {
    private val birthDate = LocalDate.of(1996, 1, 1)

    @Test
    @DisplayName("정상적인 비밀번호는 통과한다.")
    fun passWithRightPassword() {
        val rightPassword = "abf15!@#^()_+"

        Assertions.assertDoesNotThrow {
            PasswordValidator.validate(rightPassword, birthDate)
        }
    }

    @Test
    @DisplayName("8자 미만의 비밀번호는 사용할 수 없다")
    fun wrongWithUnderEightPassword() {
        val wrongPassword = "abc123!"

        assertThrows<BadRequestException> {
            PasswordValidator.validate(wrongPassword, birthDate)
        }
    }

    @Test
    @DisplayName("16자 초과의 비밀번호는 사용할 수 없다")
    fun wrongWithOverSixteenPassword() {
        val wrongPassword = "a1wbc123!1231adsf"

        assertThrows<BadRequestException> {
            PasswordValidator.validate(wrongPassword, birthDate)
        }
    }

    @Test
    @DisplayName("영문 대소문자, 숫자, 특수문자 이외의 문자가 포함된 비밀번호는 사용할 수 없다")
    fun wrongWithNotAllowedCharacterPassword() {
        val wrongPassword = "a1wbc123  !1231adsf"

        assertThrows<BadRequestException> {
            PasswordValidator.validate(wrongPassword, birthDate)
        }
    }

    @Test
    @DisplayName("생년월일이 포함된 비밀번호는 사용할 수 없다")
    fun wrongWithBirthDatePassword() {
        val wrongPassword = "ab${birthDate.format(DateTimeFormatter.BASIC_ISO_DATE)}"
        val wrongPassword2 = "ab${birthDate.format(DateTimeFormatter.BASIC_ISO_DATE).substring(2)}"

        assertThrows<BadRequestException> {
            PasswordValidator.validate(wrongPassword, birthDate)
        }

        assertThrows<BadRequestException> {
            PasswordValidator.validate(wrongPassword2, birthDate)
        }
    }
}
