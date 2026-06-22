package com.loopers.application.like

import com.loopers.domain.like.model.Like
import com.loopers.domain.like.repository.LikeRepository
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

    fun getLikes(memberId: Long): List<Like> {
        return likeRepository.findAllByMemberId(memberId)
    }
}
