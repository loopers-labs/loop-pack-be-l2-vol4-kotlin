package com.loopers.domain.user

interface UserRepository {
    fun existsByLoginId(loginId: String): Boolean
    fun save(user: UserModel): UserModel
}
