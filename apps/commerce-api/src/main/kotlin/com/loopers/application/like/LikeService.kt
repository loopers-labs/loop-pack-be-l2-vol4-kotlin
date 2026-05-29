package com.loopers.application.like

import com.loopers.domain.like.Like
import com.loopers.domain.like.LikeRepository
import com.loopers.domain.product.dto.ProductSummary
import org.springframework.data.domain.Page
import org.springframework.stereotype.Component

@Component
class LikeService(
    private val likeRepository: LikeRepository,
) {
    fun like(memberId: Long, productId: Long): Boolean {
        return likeRepository.saveIfAbsent(
            Like(
                memberId = memberId,
                productId = productId,
            ),
        )
    }

    fun unlike(memberId: Long, productId: Long): Boolean {
        return likeRepository.deleteIfExists(memberId = memberId, productId = productId)
    }

    fun getLikedProducts(memberId: Long, page: Int, size: Int): Page<ProductSummary> {
        return likeRepository.findLikedProductSummaries(memberId = memberId, page = page, size = size)
    }
}
