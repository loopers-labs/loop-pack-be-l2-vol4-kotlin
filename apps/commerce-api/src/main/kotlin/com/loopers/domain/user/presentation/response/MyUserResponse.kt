package com.loopers.domain.user.presentation.response

import com.loopers.domain.user.application.UserInfo
import java.time.LocalDate

data class MyUserResponse(
    val loginId: String,
    val name: String,
    val birthday: LocalDate,
    val email: String,
) {
    companion object {
        fun from(info: UserInfo): MyUserResponse = MyUserResponse(
            loginId = info.loginId,
            name = maskName(info.name),
            birthday = info.birthday,
            email = info.email,
        )

        private fun maskName(name: String): String =
            if (name.length == 1) {
                "*"
            } else {
                name.dropLast(1) + "*"
            }
    }
}
