package com.loopers.account.persistence

import com.loopers.account.domain.Account
import com.loopers.account.domain.AccountRepository
import com.loopers.account.domain.vo.Email
import org.springframework.stereotype.Component

@Component
class AccountRepositoryImpl(
    private val accountJpaRepository: AccountJpaRepository,
) : AccountRepository {
    override fun findById(id: Long): Account? =
        accountJpaRepository.findById(id).orElse(null)

    override fun existsByEmail(email: Email): Boolean =
        accountJpaRepository.existsByEmailValue(email.value)

    override fun save(account: Account): Account =
        accountJpaRepository.save(account)
}
