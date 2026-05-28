package com.loopers.application.user

import com.loopers.domain.user.User
import java.time.LocalDate

data class UserInfo(
    val loginId: String,
    val name: String,
    val birthDate: LocalDate,
    val email: String,
) {
    companion object {
        fun from(user: User): UserInfo {
            return UserInfo(
                loginId = user.loginId,
                name = user.name,
                birthDate = user.birthDate,
                email = user.email,
            )
        }
    }
}
