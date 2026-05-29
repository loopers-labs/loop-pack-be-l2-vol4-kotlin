package com.loopers.infrastructure.catalog

import com.loopers.domain.catalog.Product
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ProductJpaRepository : JpaRepository<Product, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): Product?

    @Query(
        """
        select count(p) > 0
          from Product p
         where p.deletedAt is null
           and p.brandId = :brandId
           and p.name = :name
        """,
    )
    fun existsActiveNameInBrand(
        @Param("brandId") brandId: Long,
        @Param("name") name: String,
    ): Boolean
}
