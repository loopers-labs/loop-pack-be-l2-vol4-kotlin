package com.loopers.account.persistence

import com.loopers.account.domain.Account
import org.springframework.data.jpa.repository.JpaRepository

interface AccountJpaRepository : JpaRepository<Account, Long>
