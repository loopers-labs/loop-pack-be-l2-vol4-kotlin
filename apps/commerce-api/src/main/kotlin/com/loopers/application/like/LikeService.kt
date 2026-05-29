package com.loopers.application.like

import com.loopers.domain.like.Like
import com.loopers.domain.like.LikeRepository
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
}
