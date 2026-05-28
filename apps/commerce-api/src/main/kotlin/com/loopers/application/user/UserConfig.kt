package com.loopers.application.user

import com.loopers.domain.user.UserRepositoryPort
import com.loopers.domain.user.UserService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class UserConfig {
    @Bean
    fun userService(userRepositoryPort: UserRepositoryPort): UserService =
        UserService(userRepositoryPort)
}
