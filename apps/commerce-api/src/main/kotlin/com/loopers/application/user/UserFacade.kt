package com.loopers.application.user

import com.loopers.domain.auth.AuthService
import com.loopers.domain.user.UserService
import org.springframework.stereotype.Component

@Component
class UserFacade(
    private val userService: UserService,
    private val authService: AuthService,
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

    fun getMyInfo(loginId: String, password: String): UserInfo {
        authService.authenticate(loginId, password)
        return userService.getMyInfo(loginId)
            .let { UserInfo.fromWithMasking(it) }
    }

    fun changePassword(loginId: String, currentPassword: String, newPassword: String) {
        authService.authenticate(loginId, currentPassword)
        userService.changePassword(loginId, newPassword)
    }
}
