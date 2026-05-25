package com.loopers.domain.user

import java.time.LocalDate

data class User(
    val id: Long = 0L,
    val name: String,
    val birth: LocalDate,
    val email: String,
) {
    companion object {
        fun create(name: String, birth: LocalDate, email: String): User =
            User(name = name, birth = birth, email = email)
    }
}
