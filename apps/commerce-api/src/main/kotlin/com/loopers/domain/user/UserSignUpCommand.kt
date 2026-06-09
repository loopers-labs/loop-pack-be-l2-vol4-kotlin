package com.loopers.domain.user

import java.time.LocalDate

data class UserSignUpCommand(
    val loginId: String,
    val rawPassword: String,
    val name: String,
    val birthDate: LocalDate,
    val email: String,
)
