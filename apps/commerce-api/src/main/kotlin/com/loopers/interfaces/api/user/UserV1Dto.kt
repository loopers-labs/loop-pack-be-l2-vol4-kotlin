package com.loopers.interfaces.api.user

import com.loopers.application.user.UserInfo
import com.loopers.domain.user.UserSignUpCommand
import java.time.LocalDate

class UserV1Dto {
    data class SignUpRequest(
        val loginId: String,
        val password: String,
        val name: String,
        val birthDate: LocalDate,
        val email: String,
    ) {
        fun toCommand(): UserSignUpCommand {
            return UserSignUpCommand(
                loginId = loginId,
                rawPassword = password,
                name = name,
                birthDate = birthDate,
                email = email,
            )
        }
    }

    data class SignUpResponse(
        val loginId: String,
        val name: String,
        val birthDate: LocalDate,
        val email: String,
    ) {
        companion object {
            fun from(info: UserInfo): SignUpResponse {
                return SignUpResponse(
                    loginId = info.loginId,
                    name = info.name,
                    birthDate = info.birthDate,
                    email = info.email,
                )
            }
        }
    }

    data class GetMeResponse(
        val loginId: String,
        val name: String,
        val birthDate: LocalDate,
        val email: String,
    ) {
        companion object {
            fun from(info: UserInfo): GetMeResponse {
                return GetMeResponse(
                    loginId = info.loginId,
                    name = info.name.dropLast(1) + "*",
                    birthDate = info.birthDate,
                    email = info.email,
                )
            }
        }
    }

    data class UpdatePasswordRequest(
        val newPassword: String,
    )
}
