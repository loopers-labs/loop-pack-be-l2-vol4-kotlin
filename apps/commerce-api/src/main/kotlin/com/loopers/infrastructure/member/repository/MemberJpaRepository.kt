package com.loopers.infrastructure.member.repository

import com.loopers.infrastructure.member.entity.MemberEntity
import org.springframework.data.jpa.repository.JpaRepository

interface MemberJpaRepository : JpaRepository<MemberEntity, Long> {
    fun existsByLoginId(loginId: String): Boolean

    fun findByLoginId(loginId: String): MemberEntity?
}
