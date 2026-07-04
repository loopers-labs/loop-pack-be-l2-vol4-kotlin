package com.loopers.domain.user.vo

import com.loopers.domain.user.constant.UserErrorMessages
import com.loopers.domain.user.exception.InvalidUserException

@JvmInline
value class Email private constructor(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        private val PATTERN = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

        fun of(value: String): Email {
            validate(value)
            return Email(value)
        }

        private fun validate(value: String) {
            if (!PATTERN.matches(value)) {
                throw InvalidUserException(UserErrorMessages.INVALID_EMAIL_FORMAT)
            }
        }
    }
}
