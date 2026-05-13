package com.loopers.interfaces.api.user

import com.loopers.domain.user.User
import java.time.LocalDate

class UserV1Dto {
    data class UserInfoResponse(
        val loginId: String,
        val name: String,
        val birth: LocalDate,
        val email: String,
    ) {
        companion object {
            fun from(user: User): UserInfoResponse = UserInfoResponse(
                loginId = user.loginId,
                name = user.name.dropLast(1) + "*",
                birth = user.birth,
                email = user.email,
            )
        }
    }

    data class SignupRequest(
        val id: String,
        val pw: String,
        val name: String,
        val birth: LocalDate,
        val email: String,
    ) {
        fun toDomain(): User = User.create(
            loginId = id,
            rawPassword = pw,
            name = name,
            birth = birth,
            email = email,
        )
    }
}
