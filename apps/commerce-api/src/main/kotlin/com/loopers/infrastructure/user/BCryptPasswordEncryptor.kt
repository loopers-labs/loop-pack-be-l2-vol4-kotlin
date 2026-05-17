package com.loopers.infrastructure.user

import com.loopers.domain.user.PasswordEncryptor
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component

@Component
class BCryptPasswordEncryptor : PasswordEncryptor {

    private val encoder = BCryptPasswordEncoder()

    override fun encode(raw: String): String = encoder.encode(raw)

    override fun matches(raw: String, encoded: String): Boolean = encoder.matches(raw, encoded)
}
