package com.loopers.domain.like

import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult

class LikeService(
    private val likeRepositoryPort: LikeRepositoryPort,
) {
    fun register(userId: Long, productId: Long) {
        if (likeRepositoryPort.findByUserIdAndProductId(userId, productId) != null) {
            return
        }
        likeRepositoryPort.save(Like(userId = userId, productId = productId))
    }

    fun cancel(userId: Long, productId: Long) {
        val existing = likeRepositoryPort.findByUserIdAndProductId(userId, productId) ?: return
        likeRepositoryPort.delete(existing)
    }

    fun deleteAllByProductId(productId: Long) {
        likeRepositoryPort.deleteAllByProductId(productId)
    }

    fun initializeForProduct(productId: Long) {
        likeRepositoryPort.initializeForProduct(productId)
    }

    fun findAllByUserId(userId: Long, pageRequest: PageRequest): PageResult<Like> =
        likeRepositoryPort.findAllByUserId(userId, pageRequest)

    fun findAllProductIdsByUserId(userId: Long): List<Long> =
        likeRepositoryPort.findAllProductIdsByUserId(userId)
}
