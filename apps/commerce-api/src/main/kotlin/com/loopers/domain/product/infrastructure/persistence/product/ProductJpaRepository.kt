package com.loopers.domain.product.infrastructure.persistence.product

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ProductJpaRepository : JpaRepository<ProductJpaEntity, Long> {
    @Query("select p from ProductJpaEntity p where p.id in :productIds")
    fun findAllByIds(
        @Param("productIds") productIds: Collection<Long>,
    ): List<ProductJpaEntity>

    fun findByBrandId(brandId: Long): List<ProductJpaEntity>

    @Query(
        """
        select p
        from ProductJpaEntity p
        where p.deletedAt is null
          and (:brandId is null or p.brandId = :brandId)
          and exists (
              select b.id
              from BrandJpaEntity b
              where b.id = p.brandId
                and b.deletedAt is null
          )
        """,
    )
    fun findActiveProducts(
        @Param("brandId") brandId: Long?,
        pageable: Pageable,
    ): List<ProductJpaEntity>
}
