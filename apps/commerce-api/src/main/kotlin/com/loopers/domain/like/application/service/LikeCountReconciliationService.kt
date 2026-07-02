package com.loopers.domain.like.application.service

import com.loopers.domain.like.port.ProductLikeCountRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class LikeCountReconciliationService(
    private val productLikeCountRepository: ProductLikeCountRepository,
) {
    @Transactional
    fun rebuildFromLikes(): LikeCountReconciliationResult {
        productLikeCountRepository.rebuildFromLikes()
        return LikeCountReconciliationResult(
            productRows = productLikeCountRepository.countProductRows(),
            likeRows = productLikeCountRepository.countLikeRows(),
            projectionRows = productLikeCountRepository.countProjectionRows(),
        )
    }
}

data class LikeCountReconciliationResult(
    val productRows: Long,
    val likeRows: Long,
    val projectionRows: Long,
)
