package com.loopers.domain.user

class User(
    val id: Long = 0,
    val loginId: LoginId,
    val password: String,
    val name: Name,
    val birthDate: BirthDate,
    val email: Email,
)
