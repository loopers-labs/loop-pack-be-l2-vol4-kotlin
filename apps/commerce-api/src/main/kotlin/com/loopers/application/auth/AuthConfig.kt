package com.loopers.application.auth

import com.loopers.domain.auth.AuthRepositoryPort
import com.loopers.domain.auth.AuthService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AuthConfig {
    @Bean
    fun authService(authRepositoryPort: AuthRepositoryPort): AuthService =
        AuthService(authRepositoryPort)
}
