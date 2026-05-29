package com.loopers.application.user

import com.loopers.domain.user.User
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.time.LocalDate

data class UserInfo(
    val id: Long,
    val loginId: String,
    val name: String,
    val birthDate: LocalDate,
    val email: String,
) {
    companion object {
        fun from(user: User): UserInfo = UserInfo(
            id = user.id ?: throw CoreException(ErrorType.INTERNAL_ERROR, "유저 ID가 존재하지 않습니다."),
            loginId = user.loginId,
            name = user.name,
            birthDate = user.birthDate,
            email = user.email,
        )
    }
}
