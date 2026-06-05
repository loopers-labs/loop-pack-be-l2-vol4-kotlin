package com.loopers.domain.user.application.info

import com.loopers.domain.user.model.UserModel
import java.time.LocalDate

data class UserInfo(
    val id: Long,
    val loginId: String,
    val name: String,
    val birthday: LocalDate,
    val email: String,
) {
    companion object {
        fun from(user: UserModel): UserInfo = UserInfo(
            id = user.id,
            loginId = user.loginId.value,
            name = user.name.value,
            birthday = user.birthday.value,
            email = user.email.value,
        )
    }
}
