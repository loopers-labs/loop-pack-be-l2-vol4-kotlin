package com.loopers.domain.like

interface LikeRepository {
    fun save(like: LikeModel): LikeModel
    fun findByUserIdAndProductId(userId: Long, productId: Long): LikeModel?
    fun delete(like: LikeModel)
    fun countByProductId(productId: Long): Long
}
