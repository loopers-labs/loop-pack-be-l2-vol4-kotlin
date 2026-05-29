package com.loopers.infrastructure.like

interface LikeCommandRepository {
    fun createIfAbsent(userId: Long, productId: Long): Int

    fun restoreIfCanceled(userId: Long, productId: Long): Int

    fun cancelIfActive(userId: Long, productId: Long): Int
}
