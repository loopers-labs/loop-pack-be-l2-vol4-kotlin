package com.loopers.domain.user.application.command

import java.time.LocalDate

data class UserSignUpCommand(
    val loginId: String,
    val rawPassword: String,
    val name: String,
    val birthday: LocalDate,
    val email: String,
)
