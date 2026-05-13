package com.loopers.domain.member

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object PasswordPolicy {
    private val ALLOWED_PASSWORD_REGEX = Regex("^[A-Za-z0-9\\p{Punct}]+$")

    private val BIRTH_DATE_PASSWORD_FORMATS = listOf(
        DateTimeFormatter.ofPattern("yyyyMMdd"),
        DateTimeFormatter.ofPattern("yyMMdd"),
    )

    fun validate(rawPassword: String, birthDate: LocalDate) {
        if (rawPassword.length !in 8..16) {
            throw CoreException(ErrorType.BAD_REQUEST, "Password length must be between 8 and 16")
        }
        if (!rawPassword.matches(ALLOWED_PASSWORD_REGEX)) {
            throw CoreException(ErrorType.BAD_REQUEST, "Password contains invalid characters.")
        }
        if (containsBirthDate(rawPassword, birthDate)) {
            throw CoreException(ErrorType.BAD_REQUEST, "Password must not contain birth date")
        }
    }

    private fun containsBirthDate(
        rawPassword: String,
        birthDate: LocalDate,
    ): Boolean {
        return BIRTH_DATE_PASSWORD_FORMATS
            .map { birthDate.format(it) }
            .any { rawPassword.contains(it) }
    }
}
