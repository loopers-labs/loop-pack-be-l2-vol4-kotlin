package com.loopers.domain.like

import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult

interface LikeRepositoryPort {
    fun findByUserIdAndProductId(userId: Long, productId: Long): Like?
    fun save(like: Like): Like
    fun delete(like: Like)
    fun deleteAllByProductId(productId: Long)
    fun initializeForProduct(productId: Long)
    fun findAllByUserId(userId: Long, pageRequest: PageRequest): PageResult<Like>
    fun findAllProductIdsByUserId(userId: Long): List<Long>
}
