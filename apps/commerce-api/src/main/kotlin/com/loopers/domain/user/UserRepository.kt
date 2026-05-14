package com.loopers.domain.user

interface UserRepository {
    fun findById(id: Long): UserModel?
    fun findByLoginId(loginId: String): UserModel?
    fun save(user: UserModel): UserModel
}