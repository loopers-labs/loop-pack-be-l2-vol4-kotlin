package com.loopers.domain.auth

interface AuthRepositoryPort {
    fun findByLoginId(loginId: String): Auth?
    fun findByUserId(userId: Long): Auth?
    fun findAllByUserIdIn(userIds: Collection<Long>): List<Auth>
    fun existsByLoginId(loginId: String): Boolean
    fun save(auth: Auth): Auth
}
