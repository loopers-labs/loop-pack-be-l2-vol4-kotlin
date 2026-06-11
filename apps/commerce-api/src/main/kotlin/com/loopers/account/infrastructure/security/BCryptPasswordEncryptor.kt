package com.loopers.account.infrastructure.security

import com.loopers.account.domain.PasswordEncryptor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
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

@Configuration
class PasswordEncoderConfig {
    @Bean
    fun passwordEncoder(): PasswordEncoder =
        BCryptPasswordEncoder()
}
