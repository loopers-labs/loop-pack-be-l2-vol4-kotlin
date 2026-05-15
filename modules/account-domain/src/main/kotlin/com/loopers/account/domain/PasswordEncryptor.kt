package com.loopers.account.domain

interface PasswordEncryptor {
    fun encode(rawPassword: String): String
}
