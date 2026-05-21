package com.loopers.account.domain

import com.loopers.domain.BaseEntity
import com.loopers.account.domain.vo.CredentialIdentifier
import com.loopers.account.domain.vo.CredentialSecret
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "account_credential",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_account_credential_method_identifier",
            columnNames = ["method", "identifier"],
        ),
    ],
)
class AccountCredential(
    account: Account,
    method: CredentialMethod,
    identifier: CredentialIdentifier,
    secret: CredentialSecret,
) : BaseEntity() {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    var account: Account = account
        private set

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 50)
    var method: CredentialMethod = method
        private set

    @Embedded
    var identifier: CredentialIdentifier = identifier
        private set

    @Embedded
    var secret: CredentialSecret = secret
        private set

    fun changeSecret(newSecret: CredentialSecret) {
        this.secret = newSecret
    }
}
