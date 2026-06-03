package com.loopers.interfaces.api.user

import com.loopers.application.user.ChangePwCommand
import com.loopers.application.user.SignupCommand
import com.loopers.domain.user.User
import com.loopers.domain.user.UserInfo

interface UserApplicationServicePort {
    fun signup(command: SignupCommand): User
    fun getMyInfo(loginId: String, rawPassword: String): UserInfo
    fun changePassword(command: ChangePwCommand)
}
