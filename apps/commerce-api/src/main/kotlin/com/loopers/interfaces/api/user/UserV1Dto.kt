package com.loopers.interfaces.api.user

import com.loopers.domain.user.RawPassword
import com.loopers.domain.user.User
import com.loopers.domain.user.UserCommand
import java.time.LocalDate

class UserV1Dto {
    data class SignUpRequest(
        val loginId: String,
        val password: String,
        val name: String,
        val birthdate: LocalDate,
        val email: String,
    ) {
        fun toCommand(): UserCommand.Register = UserCommand.Register(
            loginId = loginId,
            rawPassword = RawPassword(password),
            name = name,
            birthdate = birthdate,
            email = email,
        )
    }

    data class ChangePasswordRequest(
        val oldPassword: String,
        val newPassword: String,
    )

    data class MyInfoResponse(
        val loginId: String,
        val name: String,
        val birthdate: LocalDate,
        val email: String,
    ) {
        companion object {
            fun from(user: User): MyInfoResponse = MyInfoResponse(
                loginId = user.loginId,
                name = maskName(user.name),
                birthdate = user.birthdate,
                email = user.email,
            )

            private fun maskName(name: String): String = if (name.length <= 1) name else name.dropLast(1) + "*"
        }
    }
}
