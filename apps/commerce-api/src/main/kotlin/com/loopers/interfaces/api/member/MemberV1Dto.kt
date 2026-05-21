package com.loopers.interfaces.api.member

import com.loopers.application.member.MemberInfo
import com.loopers.domain.member.MemberSignUpCommand
import java.time.LocalDate

class MemberV1Dto {
    data class SignUpRequest(
        val loginId: String,
        val password: String,
        val name: String,
        val birthDate: LocalDate,
        val email: String,
    ) {
        fun toCommand(): MemberSignUpCommand {
            return MemberSignUpCommand(
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
            fun from(info: MemberInfo): SignUpResponse {
                return SignUpResponse(
                    loginId = info.loginId,
                    name = info.name,
                    birthDate = info.birthDate,
                    email = info.email,
                )
            }
        }
    }

    data class MyInfoResponse(
        val loginId: String,
        val name: String,
        val birthDate: LocalDate,
        val email: String,
    ) {
        companion object {
            fun from(info: MemberInfo): MyInfoResponse {
                return MyInfoResponse(
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
