package com.loopers.account.infrastructure

import com.loopers.account.domain.AccountCredential
import com.loopers.account.domain.CredentialMethod
import org.springframework.data.jpa.repository.JpaRepository

interface AccountCredentialJpaRepository : JpaRepository<AccountCredential, Long> {
    fun findByMethodAndIdentifierValue(
        method: CredentialMethod,
        identifier: String,
    ): AccountCredential?

    fun existsByMethodAndIdentifierValue(
        method: CredentialMethod,
        identifier: String,
    ): Boolean
}
