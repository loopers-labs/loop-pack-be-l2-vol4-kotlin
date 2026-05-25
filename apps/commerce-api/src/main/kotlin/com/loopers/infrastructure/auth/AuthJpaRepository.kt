package com.loopers.infrastructure.auth

import org.springframework.data.jpa.repository.JpaRepository

interface AuthJpaRepository : JpaRepository<AuthEntity, Long> {
    fun findByLoginId(loginId: String): AuthEntity?
    fun findByUserId(userId: Long): AuthEntity?
    fun existsByLoginId(loginId: String): Boolean
}
