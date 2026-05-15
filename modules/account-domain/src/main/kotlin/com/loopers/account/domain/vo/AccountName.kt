package com.loopers.account.domain.vo

import com.loopers.account.domain.error.AccountErrorCode
import com.loopers.support.error.BadRequestException
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
class AccountName(
    value: String,
) {
    @Column(name = "name", nullable = false, length = 100)
    var value: String = value
        protected set

    init {
        if (value.isBlank()) {
            throw BadRequestException(AccountErrorCode.INVALID_ACCOUNT_NAME)
        }
    }

    fun masked(): String =
        if (value.length == 1) {
            "*"
        } else {
            value.dropLast(1) + "*"
        }

    override fun equals(other: Any?): Boolean =
        this === other || other is AccountName && value == other.value

    override fun hashCode(): Int =
        value.hashCode()

    override fun toString(): String =
        value
}
