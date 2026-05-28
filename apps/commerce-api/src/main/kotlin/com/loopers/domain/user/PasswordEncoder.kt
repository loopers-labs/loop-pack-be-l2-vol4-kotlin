package com.loopers.domain.user

import at.favre.lib.crypto.bcrypt.BCrypt

object PasswordEncoder {
    fun encode(rawPassword: String): String {
        return BCrypt.withDefaults()
            .hashToString(12, rawPassword.toCharArray())
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
