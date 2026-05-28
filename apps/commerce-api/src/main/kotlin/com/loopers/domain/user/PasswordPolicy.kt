package com.loopers.domain.user

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
    private const val MIN_PASSWORD_LENGTH = 8
    private const val MAX_PASSWORD_LENGTH = 16

    fun validate(
        rawPassword: String,
        birthDate: LocalDate,
    ) {
        if (rawPassword.length !in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH) {
            throw CoreException(ErrorType.BAD_REQUEST, "Password must be 8 to 16 characters long.")
        }
        if (!rawPassword.matches(ALLOWED_PASSWORD_REGEX)) {
            throw CoreException(ErrorType.BAD_REQUEST, "Password contains unsupported characters.")
        }

        if (containsBirthDate(rawPassword, birthDate)) {
            throw CoreException(ErrorType.BAD_REQUEST, "Password must not contain birth date.")
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
