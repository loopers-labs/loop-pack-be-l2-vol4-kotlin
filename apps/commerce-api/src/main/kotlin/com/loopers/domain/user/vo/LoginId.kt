package com.loopers.domain.user.vo

import com.loopers.domain.user.exception.InvalidUserException

class LoginId private constructor(
    val value: String,
) {
    override fun equals(other: Any?): Boolean = other is LoginId && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value

    companion object {
        private val PATTERN = Regex("^[A-Za-z0-9]{4,20}$")

        fun of(value: String): LoginId {
            if (!PATTERN.matches(value)) {
                throw InvalidUserException("로그인 ID는 영문/숫자 4~20자여야 합니다.")
            }
            return LoginId(value)
        }
    }
}
