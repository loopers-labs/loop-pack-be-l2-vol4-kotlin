package com.loopers.application.user

import com.loopers.domain.user.UserModel
import java.time.LocalDate

data class UserInfo(
    val id: Long,
    val loginId: String,
    val name: String,
    val birthDate: LocalDate,
    val email: String,
) {
    companion object {
        fun from(user: UserModel): UserInfo = UserInfo(
            id = user.id,
            loginId = user.loginId,
            name = user.name,
            birthDate = user.birthDate,
            email = user.email,
        )
    }
}
