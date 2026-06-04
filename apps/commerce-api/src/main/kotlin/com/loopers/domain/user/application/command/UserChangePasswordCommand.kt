package com.loopers.domain.user.application.command

data class UserChangePasswordCommand(
    val userId: Long,
    val currentRawPassword: String,
    val newRawPassword: String,
)
