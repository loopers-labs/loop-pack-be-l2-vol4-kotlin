package com.loopers.account.infrastructure

import com.loopers.account.domain.Account
import org.springframework.data.jpa.repository.JpaRepository

interface AccountJpaRepository : JpaRepository<Account, Long> {
    fun existsByEmailValue(email: String): Boolean
}
