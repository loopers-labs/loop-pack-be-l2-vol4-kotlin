package com.loopers.domain.account.validator

import com.loopers.support.error.AccountErrorCode
import com.loopers.support.error.BadRequestException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object PasswordValidator {

    private const val MIN_PASSWORD_LENGTH = 8
    private const val MAX_PASSWORD_LENGTH = 16
    private val BIRTH_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE
    private val ALLOWED_PASSWORD_PATTERN =
        Regex("^[!-~]{8,16}$")

    fun validate(
        rawPassword: String,
        birthDate: LocalDate,
    ) {
        if (rawPassword.length !in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH) {
            throw BadRequestException(AccountErrorCode.INVALID_PASSWORD)
        }

        if (!rawPassword.matches(ALLOWED_PASSWORD_PATTERN)) {
            throw BadRequestException(AccountErrorCode.INVALID_PASSWORD)
        }

        if (rawPassword.contains(birthDate.format(BIRTH_DATE_FORMATTER).substring(2))) {
            throw BadRequestException(AccountErrorCode.INVALID_PASSWORD)
        }
    }
}
