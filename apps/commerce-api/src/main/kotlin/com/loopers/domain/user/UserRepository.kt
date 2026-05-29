package com.loopers.domain.user

interface UserRepository {
    fun existsByLoginId(loginId: String): Boolean
    fun find(id: Long): User?
    fun findByLoginId(loginId: String): User?
    fun save(user: User): User
}
