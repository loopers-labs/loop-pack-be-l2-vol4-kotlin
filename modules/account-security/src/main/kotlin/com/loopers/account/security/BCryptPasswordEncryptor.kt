package com.loopers.account.security

import com.loopers.account.domain.PasswordEncryptor
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class BCryptPasswordEncryptor(
    private val passwordEncoder: PasswordEncoder,
) : PasswordEncryptor {
    override fun encode(rawPassword: String): String =
        passwordEncoder.encode(rawPassword)

    override fun matches(
        rawPassword: String,
        encodedPassword: String,
    ): Boolean =
        passwordEncoder.matches(rawPassword, encodedPassword)
}
