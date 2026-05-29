package com.loopers.application.like

import com.loopers.domain.like.Like
import com.loopers.domain.like.LikeRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class LikeApplicationService(
    private val likeRepository: LikeRepository,
) {
    @Transactional
    fun activate(userId: Long, productId: Long): Boolean {
        val like = likeRepository.findByUserIdAndProductId(userId = userId, productId = productId)
            ?: return likeRepository.createIfAbsent(Like(userId = userId, productId = productId))

        if (!like.canActivate()) return false

        return likeRepository.restoreIfCanceled(userId = userId, productId = productId)
    }

    @Transactional
    fun cancel(userId: Long, productId: Long): Boolean {
        val like = likeRepository.findByUserIdAndProductId(userId = userId, productId = productId)
            ?: return false

        if (!like.canCancel()) return false

        return likeRepository.cancelIfActive(userId = userId, productId = productId)
    }
}
