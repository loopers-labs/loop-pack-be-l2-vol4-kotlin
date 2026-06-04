package com.loopers.domain.user.model

import com.loopers.domain.user.vo.Birthday
import com.loopers.domain.user.vo.Email
import com.loopers.domain.user.vo.LoginId
import com.loopers.domain.user.vo.Name
import com.loopers.domain.user.vo.Password

class UserModel(
    val id: Long = 0,
    val loginId: LoginId,
    val password: Password,
    val name: Name,
    val birthday: Birthday,
    val email: Email,
)
