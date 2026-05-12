package com.loopers.application.user

import com.loopers.domain.user.User

interface UserRepository {
    fun findByLoginId(loginId: String): User
    fun save(user: User): User
}
