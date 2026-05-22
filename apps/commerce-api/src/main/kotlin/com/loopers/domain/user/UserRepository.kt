package com.loopers.domain.user

interface UserRepository {
    fun findByLoginId(loginId: String): User?

    fun existsByLoginId(loginId: String): Boolean

    fun findById(id: Long): User?

    fun save(user: User): User
}
