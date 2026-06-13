package com.loopers.application.user

import com.loopers.domain.user.UserModel

data class UserInfo(
    val id: Long,
    val loginId: String,
    val name: String,
    val maskedName: String,
    val birthDate: String,
    val email: String,
) {
    companion object {
        fun from(model: UserModel) = UserInfo(
            id = model.id,
            loginId = model.loginId,
            name = model.name,
            maskedName = model.maskedName(),
            birthDate = model.birthDate,
            email = model.email,
        )
    }
}
