package com.loopers.domain.user.presentation.request

import com.loopers.domain.user.application.command.UserChangePasswordCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ChangePasswordRequest(
    @field:NotBlank(message = "새 비밀번호는 필수입니다.")
    @field:Size(min = 8, max = 16, message = "새 비밀번호는 8~16자여야 합니다.")
    val newPassword: String,
) {
    fun toCommand(
        userId: Long,
        currentRawPassword: String,
    ): UserChangePasswordCommand = UserChangePasswordCommand(
        userId = userId,
        currentRawPassword = currentRawPassword,
        newRawPassword = newPassword,
    )
}
