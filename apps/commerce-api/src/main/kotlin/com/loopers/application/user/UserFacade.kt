package com.loopers.application.user

import com.loopers.domain.user.UserService
import org.springframework.stereotype.Component

@Component
class UserFacade(
    private val userService: UserService,
) {
    fun register(loginId: String, password: String, name: String, birthDate: String, email: String): UserInfo {
        return userService.register(
            loginId = loginId,
            password = password,
            name = name,
            birthDate = birthDate,
            email = email,
        ).let { UserInfo.from(it) }
    }
}