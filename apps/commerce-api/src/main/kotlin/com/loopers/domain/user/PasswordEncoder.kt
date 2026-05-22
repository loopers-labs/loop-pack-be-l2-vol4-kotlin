package com.loopers.domain.user

interface PasswordEncoder {
    fun encode(raw: RawPassword): String

    fun matches(raw: RawPassword, encoded: String): Boolean
}
