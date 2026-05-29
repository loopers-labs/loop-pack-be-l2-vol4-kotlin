package com.loopers.domain.like

import com.loopers.domain.product.dto.ProductSummary
import org.springframework.data.domain.Page

interface LikeRepository {
    fun saveIfAbsent(like: Like): Boolean

    fun deleteIfExists(memberId: Long, productId: Long): Boolean

    fun findLikedProductSummaries(memberId: Long, page: Int, size: Int): Page<ProductSummary>
}
