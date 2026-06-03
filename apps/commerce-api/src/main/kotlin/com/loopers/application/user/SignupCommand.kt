package com.loopers.application.user

import java.time.LocalDate

data class SignupCommand(
    val loginId: String,
    val rawPassword: String,
    val name: String,
    val birth: LocalDate,
    val email: String,
)
