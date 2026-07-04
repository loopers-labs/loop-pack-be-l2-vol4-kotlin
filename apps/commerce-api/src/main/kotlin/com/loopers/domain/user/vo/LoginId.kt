package com.loopers.domain.user.vo

import com.loopers.domain.user.constant.UserErrorMessages
import com.loopers.domain.user.exception.InvalidUserException

@JvmInline
value class LoginId private constructor(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        private val PATTERN = Regex("^[A-Za-z0-9]{4,20}$")

        fun of(value: String): LoginId {
            validate(value)
            return LoginId(value)
        }

        private fun validate(value: String) {
            if (!PATTERN.matches(value)) {
                throw InvalidUserException(UserErrorMessages.INVALID_LOGIN_ID_FORMAT)
            }
        }
    }
}
