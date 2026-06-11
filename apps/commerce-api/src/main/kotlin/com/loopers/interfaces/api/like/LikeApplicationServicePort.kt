package com.loopers.interfaces.api.like

import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult
import com.loopers.domain.like.LikedProductSummary

interface LikeApplicationServicePort {
    fun like(userId: Long, productId: Long)
    fun unlike(userId: Long, productId: Long)
    fun getLikedProducts(
        targetUserId: Long,
        requesterUserId: Long,
        pageRequest: PageRequest,
    ): PageResult<LikedProductSummary>
}
