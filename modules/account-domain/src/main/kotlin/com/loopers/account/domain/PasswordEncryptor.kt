package com.loopers.account.domain

interface PasswordEncryptor {
    fun encode(rawPassword: String): String

    fun matches(
        rawPassword: String,
        encodedPassword: String,
    ): Boolean
}
