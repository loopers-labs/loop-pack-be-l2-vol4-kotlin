package com.loopers.infrastructure.brand

import com.loopers.domain.brand.Brand
import org.springframework.data.domain.Limit
import org.springframework.data.domain.ScrollPosition
import org.springframework.data.domain.Sort
import org.springframework.data.domain.Window
import org.springframework.data.jpa.repository.JpaRepository

interface BrandJpaRepository : JpaRepository<Brand, Long> {
    fun existsByNameValue(name: String): Boolean

    fun existsByNameValueAndIdNot(name: String, id: Long): Boolean

    fun findByIdAndDeletedAtIsNull(id: Long): Brand?

    fun findByDeletedAtIsNull(
        scrollPosition: ScrollPosition,
        limit: Limit,
        sort: Sort,
    ): Window<Brand>
}
