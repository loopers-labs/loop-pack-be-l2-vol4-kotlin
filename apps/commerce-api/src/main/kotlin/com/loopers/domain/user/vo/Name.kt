package com.loopers.domain.user.vo

import com.loopers.domain.user.exception.InvalidUserException

class Name private constructor(
    val value: String,
) {
    override fun equals(other: Any?): Boolean = other is Name && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value

    companion object {
        private const val MAX_LENGTH = 50

        fun of(value: String): Name {
            if (value.isBlank() || value.length > MAX_LENGTH) {
                throw InvalidUserException("이름은 공백이 아닌 1~${MAX_LENGTH}자여야 합니다.")
            }
            return Name(value)
        }
    }
}
