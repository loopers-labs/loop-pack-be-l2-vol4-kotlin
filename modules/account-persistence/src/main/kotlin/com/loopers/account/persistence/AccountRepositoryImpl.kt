package com.loopers.account.persistence

import com.loopers.account.domain.Account
import com.loopers.account.domain.AccountRepository
import org.springframework.stereotype.Component

@Component
class AccountRepositoryImpl(
    private val accountJpaRepository: AccountJpaRepository,
) : AccountRepository {
    override fun save(account: Account): Account =
        accountJpaRepository.save(account)
}
