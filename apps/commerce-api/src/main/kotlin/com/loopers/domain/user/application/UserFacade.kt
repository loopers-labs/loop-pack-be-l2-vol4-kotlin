package com.loopers.domain.user.application

import com.loopers.domain.user.application.command.UserChangePasswordCommand
import com.loopers.domain.user.application.command.UserSignUpCommand
import com.loopers.domain.user.application.info.UserInfo
import com.loopers.domain.user.application.service.UserService
import org.springframework.stereotype.Component

@Component
class UserFacade(
    private val userService: UserService,
) {
    fun signUp(command: UserSignUpCommand): UserInfo {
        return userService.signUp(command)
            .let { UserInfo.from(it) }
    }

    fun getMe(loginId: String, rawPassword: String): UserInfo {
        return userService.getMe(loginId, rawPassword)
            .let { UserInfo.from(it) }
    }

    fun changePassword(command: UserChangePasswordCommand) {
        userService.changePassword(command)
    }
}
