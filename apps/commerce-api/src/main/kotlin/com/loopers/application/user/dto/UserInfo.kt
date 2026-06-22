package com.loopers.application.user.dto

import com.loopers.domain.user.model.User
import java.time.LocalDate

data class UserInfo(
    val id: Long,
    val loginId: String,
    val name: String,
    val birthDate: LocalDate,
    val email: String,
) {
    companion object {
        fun from(user: User): UserInfo {
            return UserInfo(
                id = user.id,
                loginId = user.loginId,
                name = user.name,
                birthDate = user.birthDate,
                email = user.email,
            )
        }
    }
}
