package com.loopers.application.user

data class ChangePwCommand(
    val loginId: String,
    val loginPw: String,
    val prevPw: String,
    val nextPw: String,
)
