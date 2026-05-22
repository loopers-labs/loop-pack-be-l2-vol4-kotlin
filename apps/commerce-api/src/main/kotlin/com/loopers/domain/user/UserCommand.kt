package com.loopers.domain.user

import java.time.LocalDate

class UserCommand {
    data class Register(
        val loginId: String,
        val rawPassword: RawPassword,
        val name: String,
        val birthdate: LocalDate,
        val email: String,
    )
}
