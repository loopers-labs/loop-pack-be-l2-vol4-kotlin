package com.loopers.infrastructure.product.repository

import com.loopers.domain.product.ProductSort
import com.loopers.domain.product.dto.ProductSummary
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface ProductQueryRepository {
    fun findDisplayableSummaries(
        brandId: Long?,
        sort: ProductSort,
        pageable: Pageable,
    ): Page<ProductSummary>
}
