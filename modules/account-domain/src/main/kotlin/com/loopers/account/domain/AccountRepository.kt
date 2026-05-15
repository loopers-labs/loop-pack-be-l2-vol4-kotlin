package com.loopers.account.domain

import com.loopers.account.domain.vo.Email

interface AccountRepository {
    fun existsByEmail(email: Email): Boolean

    fun save(account: Account): Account
}
