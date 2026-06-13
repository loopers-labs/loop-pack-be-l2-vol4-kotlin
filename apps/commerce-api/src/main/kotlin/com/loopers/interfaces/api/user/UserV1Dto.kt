package com.loopers.interfaces.api.user

import com.loopers.application.user.UserInfo

class UserV1Dto {
    data class RegisterRequest(
        val loginId: String,
        val password: String,
        val name: String,
        val birthDate: String,
        val email: String,
    )

    data class RegisterResponse(
        val id: Long,
        val loginId: String,
        val name: String,
        val birthDate: String,
        val email: String,
    ) {
        companion object {
            fun from(info: UserInfo) = RegisterResponse(
                id = info.id,
                loginId = info.loginId,
                name = info.name,
                birthDate = info.birthDate,
                email = info.email,
            )
        }
    }

    data class MeResponse(
        val loginId: String,
        val name: String,
        val birthDate: String,
        val email: String,
    ) {
        companion object {
            fun from(info: UserInfo) = MeResponse(
                loginId = info.loginId,
                name = info.maskedName,
                birthDate = info.birthDate,
                email = info.email,
            )
        }
    }

    data class ChangePasswordRequest(
        val currentPassword: String,
        val newPassword: String,
    )
}
