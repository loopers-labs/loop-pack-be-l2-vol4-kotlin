package com.loopers.account.domain

import com.loopers.account.domain.vo.CredentialIdentifier

interface AccountCredentialRepository {
    fun existsBy(
        method: CredentialMethod,
        identifier: CredentialIdentifier,
    ): Boolean

    fun findBy(
        method: CredentialMethod,
        identifier: CredentialIdentifier,
    ): AccountCredential?

    fun save(credential: AccountCredential): AccountCredential
}
