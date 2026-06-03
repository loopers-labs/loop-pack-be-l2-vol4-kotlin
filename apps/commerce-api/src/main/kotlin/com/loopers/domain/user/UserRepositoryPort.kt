package com.loopers.domain.user

interface UserRepositoryPort {
    fun findById(id: Long): User?
    fun save(user: User): User
}
