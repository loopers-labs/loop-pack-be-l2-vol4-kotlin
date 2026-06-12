package com.loopers.domain.user.port

import com.loopers.domain.user.model.UserModel
import com.loopers.domain.user.vo.Password

interface UserRepository {
    fun existsByLoginId(loginId: String): Boolean
    fun save(user: UserModel): UserModel
    fun findByIdOrNull(id: Long): UserModel?
    fun findByLoginIdOrNull(loginId: String): UserModel?
    fun findByIdForUpdateOrNull(id: Long): UserModel?
    fun updatePassword(id: Long, password: Password)
}
