package com.loopers.domain.user

interface UserPasswordEncoder {
    fun encode(rawPassword: String): String
}
