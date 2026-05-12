package com.loopers.interfaces.api.member

import com.loopers.application.user.UserInfo

class MemberV1Dto {
    data class RegisterRequest(
        val loginId: String,
        val password: String,
        val name: String,
        val birthDate: String,
        val email: String,
    )

    data class RegisterResponse(
        val loginId: String,
        val name: String,
        val birthDate: String,
        val email: String,
    ) {
        companion object {
            fun from(info: UserInfo): RegisterResponse {
                return RegisterResponse(
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
        val birthDate: String,
        val email: String,
    ) {
        companion object {
            fun from(info: UserInfo): MyInfoResponse {
                return MyInfoResponse(
                    loginId = info.loginId,
                    name = info.name,
                    birthDate = info.birthDate,
                    email = info.email,
                )
            }
        }
    }
}