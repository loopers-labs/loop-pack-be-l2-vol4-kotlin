package com.loopers.domain.member

import java.time.LocalDate

data class MemberSignUpCommand(
    val loginId: String,
    val rawPassword: String,
    val name: String,
    val birthDate: LocalDate,
    val email: String,
)
