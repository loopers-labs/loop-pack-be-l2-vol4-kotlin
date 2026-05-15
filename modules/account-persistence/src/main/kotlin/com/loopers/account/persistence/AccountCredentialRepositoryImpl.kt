package com.loopers.account.persistence

import com.loopers.account.domain.AccountCredential
import com.loopers.account.domain.AccountCredentialRepository
import com.loopers.account.domain.CredentialMethod
import com.loopers.account.domain.vo.CredentialIdentifier
import org.springframework.stereotype.Component

@Component
class AccountCredentialRepositoryImpl(
    private val accountCredentialJpaRepository: AccountCredentialJpaRepository,
) : AccountCredentialRepository {
    override fun existsBy(
        method: CredentialMethod,
        identifier: CredentialIdentifier,
    ): Boolean =
        accountCredentialJpaRepository.existsByMethodAndIdentifierValue(method, identifier.value)

    override fun save(credential: AccountCredential): AccountCredential =
        accountCredentialJpaRepository.save(credential)
}
