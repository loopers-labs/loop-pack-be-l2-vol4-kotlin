package com.loopers.account.infrastructure

import com.loopers.account.domain.AccountCredential
import com.loopers.account.domain.CredentialMethod
import com.loopers.account.domain.vo.CredentialIdentifier
import org.springframework.stereotype.Component

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

@Component
class AccountCredentialRepositoryImpl(
    private val accountCredentialJpaRepository: AccountCredentialJpaRepository,
) : AccountCredentialRepository {
    override fun existsBy(
        method: CredentialMethod,
        identifier: CredentialIdentifier,
    ): Boolean =
        accountCredentialJpaRepository.existsByMethodAndIdentifierValue(method, identifier.value)

    override fun findBy(
        method: CredentialMethod,
        identifier: CredentialIdentifier,
    ): AccountCredential? =
        accountCredentialJpaRepository.findByMethodAndIdentifierValue(method, identifier.value)

    override fun save(credential: AccountCredential): AccountCredential =
        accountCredentialJpaRepository.save(credential)
}
