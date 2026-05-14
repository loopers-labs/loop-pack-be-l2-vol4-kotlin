package com.loopers.application.user

import com.loopers.domain.user.UserService
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class UserFacade(
    private val userService: UserService,
) {
    fun signUp(
        loginId: String,
        rawPassword: String,
        name: String,
        birthDate: LocalDate,
        email: String,
    ): UserInfo {
        return userService.signUp(loginId, rawPassword, name, birthDate, email)
            .let { UserInfo.from(it) }
    }
}
