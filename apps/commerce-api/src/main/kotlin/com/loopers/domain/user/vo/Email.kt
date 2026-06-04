package com.loopers.domain.user.vo

import com.loopers.domain.user.exception.InvalidUserException

class Email private constructor(
    val value: String,
) {
    override fun equals(other: Any?): Boolean = other is Email && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value

    companion object {
        private val PATTERN = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

        fun of(value: String): Email {
            if (!PATTERN.matches(value)) {
                throw InvalidUserException("이메일 형식이 올바르지 않습니다.")
            }
            return Email(value)
        }
    }
}
