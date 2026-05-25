package com.loopers.interfaces.api.user

import com.fasterxml.jackson.annotation.JsonAlias
import com.loopers.application.user.ChangePwCommand
import com.loopers.application.user.SignupCommand
import com.loopers.application.user.UserInfo
import java.time.LocalDate

class UserV1Dto {
    data class UserInfoResponse(
        val loginId: String,
        val name: String,
        val birth: LocalDate,
        val email: String,
    ) {
        companion object {
            fun from(info: UserInfo): UserInfoResponse = UserInfoResponse(
                loginId = info.loginId,
                name = info.name.dropLast(1) + "*",
                birth = info.birth,
                email = info.email,
            )
        }
    }

    data class ChangePasswordRequest(
        @JsonAlias("oldPassword")
        val prevPw: String,
        @JsonAlias("newPassword")
        val nextPw: String,
    ) {
        fun toCommand(loginId: String, loginPw: String): ChangePwCommand = ChangePwCommand(
            loginId = loginId,
            loginPw = loginPw,
            prevPw = prevPw,
            nextPw = nextPw,
        )
    }

    data class SignupRequest(
        val id: String,
        val pw: String,
        val name: String,
        val birth: LocalDate,
        val email: String,
    ) {
        fun toCommand(): SignupCommand = SignupCommand(
            loginId = id,
            rawPassword = pw,
            name = name,
            birth = birth,
            email = email,
        )
    }
}
