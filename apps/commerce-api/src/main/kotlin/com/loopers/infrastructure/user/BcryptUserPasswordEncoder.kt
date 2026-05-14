package com.loopers.infrastructure.user

import com.loopers.domain.user.UserPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class BcryptUserPasswordEncoder(
    private val passwordEncoder: PasswordEncoder,
) : UserPasswordEncoder {
    override fun encode(rawPassword: String): String = passwordEncoder.encode(rawPassword)

    override fun matches(rawPassword: String, encodedPassword: String): Boolean =
        passwordEncoder.matches(rawPassword, encodedPassword)
}
