package com.loopers.application.user

import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class UserFacade(
    private val userApplicationService: UserApplicationService,
) {
    fun signUp(
        loginId: String,
        rawPassword: String,
        name: String,
        birthDate: LocalDate,
        email: String,
    ): UserInfo {
        return userApplicationService.signUp(loginId, rawPassword, name, birthDate, email)
            .let { UserInfo.from(it) }
    }

    fun getUserInfo(loginId: String, rawPassword: String): UserInfo {
        return userApplicationService.getUserInfo(loginId, rawPassword)
            .let { UserInfo.from(it) }
    }

    fun changePassword(loginId: String, currentRawPassword: String, newRawPassword: String) {
        userApplicationService.changePassword(loginId, currentRawPassword, newRawPassword)
    }
}
