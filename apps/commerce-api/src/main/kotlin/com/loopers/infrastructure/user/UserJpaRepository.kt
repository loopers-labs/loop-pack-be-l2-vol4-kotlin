package com.loopers.infrastructure.user

import org.springframework.data.jpa.repository.JpaRepository

interface UserJpaRepository : JpaRepository<UserJpaEntity, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): UserJpaEntity?
    fun findByLoginIdAndDeletedAtIsNull(loginId: String): UserJpaEntity?
    fun existsByLoginId(loginId: String): Boolean
}
