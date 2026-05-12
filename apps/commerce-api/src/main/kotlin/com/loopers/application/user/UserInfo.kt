package com.loopers.application.user

import com.loopers.domain.user.User

data class UserInfo(
    val loginId: String,
    val name: String,
    val birthDate: String,
    val email: String,
) {
    companion object {
        fun from(user: User): UserInfo {
            return UserInfo(
                loginId = user.loginId,
                name = user.name.dropLast(1) + "*",
                birthDate = user.birthDate,
                email = user.email,
            )
        }
    }
}