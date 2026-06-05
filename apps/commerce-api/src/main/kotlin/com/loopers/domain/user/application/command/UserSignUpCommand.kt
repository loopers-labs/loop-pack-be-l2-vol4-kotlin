package com.loopers.domain.user.application.command

import java.time.LocalDate

class UserSignUpCommand(
    val loginId: String,
    val rawPassword: String,
    val name: String,
    val birthday: LocalDate,
    val email: String,
) {
    override fun toString(): String =
        "UserSignUpCommand(loginId=$loginId, rawPassword=<masked>, name=$name, birthday=$birthday, email=<masked>)"
}
