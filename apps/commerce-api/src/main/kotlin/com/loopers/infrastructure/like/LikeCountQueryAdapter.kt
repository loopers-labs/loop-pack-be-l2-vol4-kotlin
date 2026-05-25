package com.loopers.infrastructure.like

import com.loopers.domain.product.LikeCountQueryPort
import org.springframework.stereotype.Component

@Component
class LikeCountQueryAdapter(
    private val likeCountJpaRepository: LikeCountJpaRepository,
) : LikeCountQueryPort {
    override fun countByProductId(productId: Long): Long =
        likeCountJpaRepository.findByProductId(productId)?.count?.toLong() ?: 0L
}
