package com.loopers.infrastructure.like

import com.loopers.domain.product.dto.ProductSummary
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface ProductLikeQueryRepository {
    fun findLikedProductSummaries(memberId: Long, pageable: Pageable): Page<ProductSummary>
}
