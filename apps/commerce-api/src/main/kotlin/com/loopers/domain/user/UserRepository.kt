package com.loopers.domain.user

interface UserRepository {
    fun existsByLoginId(loginId: String): Boolean
    fun findByLoginId(loginId: String): UserModel?
    fun save(user: UserModel): UserModel
}
