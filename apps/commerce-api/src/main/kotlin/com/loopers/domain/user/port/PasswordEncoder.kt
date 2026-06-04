package com.loopers.domain.user.port

interface PasswordEncoder {
    fun encode(raw: String): String

    fun matches(raw: String, encoded: String): Boolean
}
