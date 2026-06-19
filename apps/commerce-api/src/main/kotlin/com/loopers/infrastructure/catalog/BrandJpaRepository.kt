package com.loopers.infrastructure.catalog

import com.loopers.domain.catalog.Brand
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface BrandJpaRepository : JpaRepository<Brand, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): Brand?

    @Query("select count(b) > 0 from Brand b where b.deletedAt is null and b.name = :name")
    fun existsActiveName(@Param("name") name: String): Boolean
}
