package com.loopers.domain.user.vo

import com.loopers.domain.user.exception.InvalidUserException
import java.time.LocalDate

@JvmInline
value class Birthday private constructor(
    val value: LocalDate,
) {
    override fun toString(): String = value.toString()

    companion object {
        fun of(value: LocalDate): Birthday {
            validate(value)
            return Birthday(value)
        }

        private fun validate(value: LocalDate) {
            if (!value.isBefore(LocalDate.now())) {
                throw InvalidUserException("생년월일은 과거 일자여야 합니다.")
            }
        }
    }
}
