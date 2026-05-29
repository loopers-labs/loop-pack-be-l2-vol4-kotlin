package com.loopers.domain.like

interface LikeRepository {
    fun findByUserIdAndProductId(userId: Long, productId: Long): Like?

    fun createIfAbsent(like: Like): Boolean

    fun restoreIfCanceled(userId: Long, productId: Long): Boolean

    fun cancelIfActive(userId: Long, productId: Long): Boolean
}
