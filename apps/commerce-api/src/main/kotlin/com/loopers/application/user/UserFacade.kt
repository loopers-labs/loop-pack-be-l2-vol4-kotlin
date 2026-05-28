package com.loopers.application.user

import com.loopers.domain.user.UserSignUpCommand
import org.springframework.stereotype.Component

@Component
class UserFacade(
    private val userService: UserService,
) {
    fun signUp(command: UserSignUpCommand): UserInfo {
        return userService.signUp(command)
    }

    fun getMe(
        loginId: String,
        rawPassword: String,
    ): UserInfo {
        return userService.getMe(loginId, rawPassword)
    }

    fun updatePassword(
        loginId: String,
        rawPassword: String,
        newRawPassword: String,
    ) {
        userService.updatePassword(
            loginId = loginId,
            rawPassword = rawPassword,
            newRawPassword = newRawPassword,
        )
    }
}
