package com.loopers.infrastructure.brand

import org.springframework.data.jpa.repository.JpaRepository

interface BrandJpaRepository : JpaRepository<BrandEntity, Long> {
    fun existsByName(name: String): Boolean
}
