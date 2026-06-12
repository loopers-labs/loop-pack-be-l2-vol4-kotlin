package com.loopers.domain.user.infrastructure.persistence

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserJpaRepository : JpaRepository<UserJpaEntity, Long> {
    fun findByLoginId(loginId: String): UserJpaEntity?
    fun existsByLoginId(loginId: String): Boolean

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserJpaEntity u where u.id = :id")
    fun findByIdForUpdate(
        @Param("id") id: Long,
    ): UserJpaEntity?
}
