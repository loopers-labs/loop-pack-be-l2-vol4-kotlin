package com.loopers.domain.user.vo

import com.loopers.domain.user.constant.UserErrorMessages
import com.loopers.domain.user.exception.InvalidPasswordException
import com.loopers.domain.user.port.PasswordEncoder
import java.time.format.DateTimeFormatter

class Password private constructor(
    private val encoded: String,
) {
    fun matches(raw: String, encoder: PasswordEncoder): Boolean = encoder.matches(raw, encoded)

    fun encodedForPersistence(): String = encoded

    companion object {
        private val PASSWORD_REGEX =
            Regex("^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[!@#$%^&*(),.?\":{}|<>])[A-Za-z0-9!@#$%^&*(),.?\":{}|<>]{8,16}$")
        private val BIRTHDAY_TOKEN_PATTERNS = listOf("yyyyMMdd", "yyMMdd", "MMdd")

        fun of(raw: String, birthday: Birthday, encoder: PasswordEncoder): Password {
            validate(raw, birthday)
            return Password(encoder.encode(raw))
        }

        fun fromEncoded(encoded: String): Password = Password(encoded)

        private fun validate(raw: String, birthday: Birthday) {
            validateFormat(raw)
            validateNotContainsBirthday(raw, birthday)
        }

        private fun validateFormat(raw: String) {
            validate(
                PASSWORD_REGEX.matches(raw),
                UserErrorMessages.INVALID_PASSWORD_FORMAT,
            )
        }

        private fun validateNotContainsBirthday(raw: String, birthday: Birthday) {
            validate(!containsBirthdayToken(raw, birthday), UserErrorMessages.PASSWORD_CONTAINS_BIRTHDAY)
        }

        private fun containsBirthdayToken(raw: String, birthday: Birthday): Boolean =
            BIRTHDAY_TOKEN_PATTERNS.any { pattern ->
                raw.contains(birthday.value.format(DateTimeFormatter.ofPattern(pattern)))
            }

        private fun validate(condition: Boolean, message: String) {
            if (!condition) {
                throw InvalidPasswordException(message)
            }
        }
    }
}
