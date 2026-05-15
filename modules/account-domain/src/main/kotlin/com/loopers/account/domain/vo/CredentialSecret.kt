package com.loopers.account.domain.vo

import com.loopers.account.domain.error.AccountErrorCode
import com.loopers.support.error.BadRequestException
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
class CredentialSecret(
    value: String,
) {
    @Column(name = "secret", nullable = false, length = 255)
    var value: String = value
        protected set

    init {
        if (value.isBlank()) {
            throw BadRequestException(AccountErrorCode.INVALID_CREDENTIAL_SECRET)
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is CredentialSecret && value == other.value

    override fun hashCode(): Int =
        value.hashCode()

    override fun toString(): String =
        "[PROTECTED]"
}
