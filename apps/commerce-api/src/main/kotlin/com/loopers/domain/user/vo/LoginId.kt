package com.loopers.domain.user.vo

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
                throw InvalidUserException("로그인 ID는 영문/숫자 4~20자여야 합니다.")
            }
        }
    }
}
