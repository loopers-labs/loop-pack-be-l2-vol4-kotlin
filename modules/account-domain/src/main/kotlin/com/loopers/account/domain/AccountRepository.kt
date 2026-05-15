package com.loopers.account.domain

interface AccountRepository {
    fun save(account: Account): Account
}
