package com.loopers.domain.user.repository

import com.loopers.domain.user.model.User

interface UserRepository {
    fun existsByLoginId(loginId: String): Boolean

    fun findByLoginId(loginId: String): User?

    fun save(user: User): User
}
