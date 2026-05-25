package com.loopers.domain.auth

interface AuthRepositoryPort {
    fun findByLoginIdOrNull(loginId: String): Auth?
    fun findByUserIdOrNull(userId: Long): Auth?
    fun existsByLoginId(loginId: String): Boolean
    fun save(auth: Auth): Auth
}
