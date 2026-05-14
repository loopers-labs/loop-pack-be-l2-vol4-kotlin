package com.loopers.infrastructure.security

import com.loopers.domain.user.EncodedPassword
import com.loopers.domain.user.RawPassword
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class UserPasswordEncoder(
    private val passwordEncoder: PasswordEncoder,
) {
    fun encode(rawPassword: RawPassword): EncodedPassword {
        return EncodedPassword(passwordEncoder.encode(rawPassword.value))
    }
}
