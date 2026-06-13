package com.loopers.application.user

import com.loopers.domain.user.UserService
import org.springframework.stereotype.Component

@Component
class UserFacade(
    private val userService: UserService,
) {
    fun register(loginId: String, rawPassword: String, name: String, birthDate: String, email: String): UserInfo {
        return userService.register(loginId, rawPassword, name, birthDate, email)
            .let { UserInfo.from(it) }
    }

    fun authenticate(loginId: String, rawPassword: String): UserInfo {
        return userService.authenticate(loginId, rawPassword)
            .let { UserInfo.from(it) }
    }

    fun changePassword(userId: Long, currentRawPassword: String, newRawPassword: String) {
        userService.changePassword(userId, currentRawPassword, newRawPassword)
    }
}
