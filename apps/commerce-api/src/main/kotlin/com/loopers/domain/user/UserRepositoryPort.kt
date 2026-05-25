package com.loopers.domain.user

interface UserRepositoryPort {
    fun findByIdOrNull(id: Long): User?
    fun save(user: User): User
}
