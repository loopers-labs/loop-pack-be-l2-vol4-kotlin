package com.loopers.domain.like.repository

import com.loopers.domain.like.model.Like

interface LikeRepository {
    fun saveIfAbsent(like: Like): Boolean

    fun deleteIfExists(memberId: Long, productId: Long): Boolean

    fun findAllByMemberId(memberId: Long): List<Like>
}
