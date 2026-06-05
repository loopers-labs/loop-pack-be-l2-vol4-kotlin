package com.loopers.domain.user.application.command

class UserChangePasswordCommand(
    val userId: Long,
    val currentRawPassword: String,
    val newRawPassword: String,
) {
    override fun toString(): String =
        "UserChangePasswordCommand(userId=$userId, currentRawPassword=<masked>, newRawPassword=<masked>)"
}
