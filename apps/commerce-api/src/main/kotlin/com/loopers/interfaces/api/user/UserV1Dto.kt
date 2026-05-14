package com.loopers.interfaces.api.user

import com.loopers.application.user.UserInfo
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDate

class UserV1Dto {
    data class SignUpRequest(
        @field:NotBlank val loginId: String,
        @field:NotBlank val password: String,
        @field:NotBlank val name: String,
        @field:NotNull val birthDate: LocalDate,
        @field:NotBlank val email: String,
    )

    data class SignUpResponse(
        val id: Long,
        val loginId: String,
        val name: String,
        val birthDate: String,
        val email: String,
    ) {
        companion object {
            fun from(info: UserInfo): SignUpResponse = SignUpResponse(
                id = info.id,
                loginId = info.loginId,
                name = info.name,
                birthDate = info.birthDate.toString(),
                email = info.email,
            )
        }
    }

    data class GetUserInfoResponse(
        val loginId: String,
        val name: String,
        val birthDate: String,
        val email: String,
    ) {
        companion object {
            fun from(info: UserInfo): GetUserInfoResponse = GetUserInfoResponse(
                loginId = info.loginId,
                name = if (info.name.isEmpty()) "*" else info.name.dropLast(1) + "*",
                birthDate = info.birthDate.toString(),
                email = info.email,
            )
        }
    }
}
