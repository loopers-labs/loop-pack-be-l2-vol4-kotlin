package com.loopers.application.user

import org.springframework.stereotype.Component

@Component
class UserFacade(
    private val userService: UserService,
) {
    fun changePw(command: ChangePwCommand) {
        userService.login(command.loginId, command.loginPw)
        userService.changePw(command)
    }
}
