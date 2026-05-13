package com.loopers.domain.member

import at.favre.lib.crypto.bcrypt.BCrypt

object PasswordEncoder {
    private const val BCRYPT_WORK_FACTOR = 12

    fun encode(rawPassword: String): String {
        return BCrypt.withDefaults()
            .hashToString(BCRYPT_WORK_FACTOR, rawPassword.toCharArray())
    }

    fun matches(
        rawPassword: String,
        encodedPassword: String,
    ): Boolean {
        return BCrypt.verifyer()
            .verify(rawPassword.toCharArray(), encodedPassword)
            .verified
    }
}
