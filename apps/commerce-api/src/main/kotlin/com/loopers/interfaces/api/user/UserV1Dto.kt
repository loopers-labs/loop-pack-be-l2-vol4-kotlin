package com.loopers.interfaces.api.user

import com.loopers.domain.user.RawPassword
import com.loopers.domain.user.User
import com.loopers.domain.user.UserCommand
import com.loopers.domain.user.UserRole
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDate

class UserV1Dto {
    data class SignUpRequest(
        @field:NotBlank
        val loginId: String,

        @field:NotBlank
        val password: String,

        @field:NotBlank
        val name: String,

        @field:NotNull
        val birthdate: LocalDate,

        @field:NotBlank
        @field:Email
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
        @field:NotBlank
        val oldPassword: String,

        @field:NotBlank
        val newPassword: String,
    )

    data class MyInfoResponse(
        val loginId: String,
        val name: String,
        val birthdate: LocalDate,
        val email: String,
        val role: UserRole,
    ) {
        companion object {
            fun from(user: User): MyInfoResponse = MyInfoResponse(
                loginId = user.loginId,
                name = maskName(user.name),
                birthdate = user.birthdate,
                email = user.email,
                role = user.role,
            )

            private fun maskName(name: String): String = if (name.length <= 1) name else name.dropLast(1) + "*"
        }
    }
}
