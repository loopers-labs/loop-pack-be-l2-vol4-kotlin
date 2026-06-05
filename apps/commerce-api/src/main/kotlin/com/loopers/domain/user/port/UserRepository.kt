package com.loopers.domain.user.port

import com.loopers.domain.user.model.UserModel
import com.loopers.domain.user.vo.Password

interface UserRepository {
    fun existsByLoginId(loginId: String): Boolean
    fun save(user: UserModel): UserModel
    fun findById(id: Long): UserModel?
    fun findByLoginId(loginId: String): UserModel?
    fun findByIdForUpdate(id: Long): UserModel?
    fun updatePassword(id: Long, password: Password)
}
