package com.loopers.domain.member

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.time.LocalDate

object PasswordPolicy {
    fun validate(rawPassword: String, birthDate: LocalDate) {
        if (rawPassword.length !in 8..16) {
            throw CoreException(ErrorType.BAD_REQUEST, "Password length must be between 8 and 16")
        }
        if (!rawPassword.matches(ALLOWED_PASSWORD_REGEX)) {
            throw CoreException(ErrorType.BAD_REQUEST, "Password contains invalid characters.")
        }
    }

    private val ALLOWED_PASSWORD_REGEX = Regex("^[A-Za-z0-9\\p{Punct}]+$")
}
