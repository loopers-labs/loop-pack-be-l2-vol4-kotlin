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
                loginId = user.loginId.value,
                name = user.name.value,
                birthDate = user.birthDate.value,
                email = user.email.value,
            )
        }

        fun fromWithMasking(user: User): UserInfo {
            return UserInfo(
                loginId = user.loginId.value,
                name = user.name.masked(),
                birthDate = user.birthDate.value,
                email = user.email.value,
            )
        }
    }
}
