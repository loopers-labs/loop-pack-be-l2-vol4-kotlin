package com.loopers.domain.user.vo

import com.loopers.domain.user.exception.InvalidUserException
import java.time.LocalDate

class Birthday private constructor(
    val value: LocalDate,
) {
    override fun equals(other: Any?): Boolean = other is Birthday && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value.toString()

    companion object {
        fun of(value: LocalDate): Birthday {
            if (!value.isBefore(LocalDate.now())) {
                throw InvalidUserException("생년월일은 과거 일자여야 합니다.")
            }
            return Birthday(value)
        }
    }
}
