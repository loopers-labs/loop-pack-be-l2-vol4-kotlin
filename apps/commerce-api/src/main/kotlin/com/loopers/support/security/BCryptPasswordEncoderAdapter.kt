package com.loopers.support.security

import com.loopers.domain.user.port.PasswordEncoder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component

@Component
class BCryptPasswordEncoderAdapter(
    private val delegate: BCryptPasswordEncoder,
) : PasswordEncoder {
    override fun encode(raw: String): String = delegate.encode(raw)

    override fun matches(raw: String, encoded: String): Boolean = delegate.matches(raw, encoded)
}
