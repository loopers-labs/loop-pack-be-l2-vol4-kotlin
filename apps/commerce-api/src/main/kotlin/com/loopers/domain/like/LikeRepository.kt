package com.loopers.domain.like

interface LikeRepository {
    fun saveIfAbsent(like: Like): Boolean

    fun deleteIfExists(memberId: Long, productId: Long): Boolean

    fun findAllByMemberId(memberId: Long): List<Like>
}
