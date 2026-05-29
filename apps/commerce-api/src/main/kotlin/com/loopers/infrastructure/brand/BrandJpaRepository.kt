package com.loopers.infrastructure.brand

import org.springframework.data.jpa.repository.JpaRepository

interface BrandJpaRepository : JpaRepository<BrandJpaEntity, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): BrandJpaEntity?

    fun findAllByIdInAndDeletedAtIsNull(ids: Collection<Long>): List<BrandJpaEntity>
}
