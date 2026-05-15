package com.loopers.account.persistence

import com.loopers.account.domain.Account
import com.loopers.account.domain.vo.Email

interface AccountRepository {
    fun findById(id: Long): Account?

    fun existsByEmail(email: Email): Boolean

    fun save(account: Account): Account
}
