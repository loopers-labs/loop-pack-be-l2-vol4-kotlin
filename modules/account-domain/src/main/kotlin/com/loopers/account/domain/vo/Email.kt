package com.loopers.account.domain.vo

import com.loopers.account.domain.error.AccountErrorCode
import com.loopers.support.error.BadRequestException
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
class Email(
    value: String,
) {
    @Column(name = "email", nullable = false, length = 255)
    var value: String = value
        protected set

    init {
        if (!EMAIL_REGEX.matches(value)) {
            throw BadRequestException(AccountErrorCode.INVALID_EMAIL)
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is Email && value == other.value

    override fun hashCode(): Int =
        value.hashCode()

    override fun toString(): String =
        value

    companion object {
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    }
}
