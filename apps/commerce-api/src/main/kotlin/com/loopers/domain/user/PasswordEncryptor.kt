package com.loopers.domain.user

interface PasswordEncryptor {
    fun encode(raw: String): String
    fun matches(raw: String, encoded: String): Boolean
}
