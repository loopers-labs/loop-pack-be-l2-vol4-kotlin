package com.loopers.interfaces.api.like

import com.loopers.application.like.LikedProductSummary
import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult

interface LikeApplicationServicePort {
    fun like(userId: Long, productId: Long)
    fun unlike(userId: Long, productId: Long)
    fun getLikedProducts(
        targetUserId: Long,
        requesterUserId: Long,
        pageRequest: PageRequest,
    ): PageResult<LikedProductSummary>
}
