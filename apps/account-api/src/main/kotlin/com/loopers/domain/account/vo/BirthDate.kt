package com.loopers.domain.account.vo

import com.loopers.support.error.AccountErrorCode
import com.loopers.support.error.BadRequestException
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.time.LocalDate

@Embeddable
class BirthDate(
    value: LocalDate,
) {
    @Column(name = "birth_date", nullable = false)
    var value: LocalDate = value
        protected set

    init {
        if (value.isAfter(LocalDate.now())) {
            throw BadRequestException(AccountErrorCode.INVALID_BIRTH_DATE)
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is BirthDate && value == other.value

    override fun hashCode(): Int =
        value.hashCode()

    override fun toString(): String =
        value.toString()
}
