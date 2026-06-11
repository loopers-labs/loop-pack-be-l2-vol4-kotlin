package com.loopers.domain.user

import java.time.LocalDate

data class UserInfo(
    val loginId: String,
    val name: String,
    val birth: LocalDate,
    val email: String,
) {
    companion object {
        fun of(user: User, loginId: String): UserInfo = UserInfo(
            loginId = loginId,
            name = user.name,
            birth = user.birth,
            email = user.email,
        )
    }
}
