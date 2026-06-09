package com.loopers.domain.user

interface UserRepository {
    fun existsByLoginId(loginId: String): Boolean

    fun findByLoginId(loginId: String): User?

    fun save(user: User): User
}
