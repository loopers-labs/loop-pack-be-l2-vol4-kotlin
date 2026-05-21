package com.loopers.account.domain.vo

import com.loopers.account.domain.CredentialMethod
import com.loopers.account.domain.error.AccountErrorCode
import com.loopers.support.error.BadRequestException
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
class CredentialIdentifier(
    method: CredentialMethod,
    value: String,
) {
    @Column(name = "identifier", nullable = false, length = 255)
    var value: String = value
        private set

    init {
        if (value.length > 255) {
            throw BadRequestException(AccountErrorCode.INVALID_CREDENTIAL_IDENTIFIER)
        }
        if (method == CredentialMethod.PASSWORD && !PASSWORD_IDENTIFIER_REGEX.matches(value)) {
            throw BadRequestException(AccountErrorCode.INVALID_CREDENTIAL_IDENTIFIER)
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is CredentialIdentifier && value == other.value

    override fun hashCode(): Int =
        value.hashCode()

    override fun toString(): String =
        value

    companion object {
        private val PASSWORD_IDENTIFIER_REGEX = Regex("^[A-Za-z0-9]+$")
    }
}
