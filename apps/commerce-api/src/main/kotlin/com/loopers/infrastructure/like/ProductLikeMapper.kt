package com.loopers.infrastructure.like

import com.loopers.domain.like.Like

object ProductLikeMapper {
    fun toEntity(like: Like): ProductLikeEntity {
        return ProductLikeEntity(
            memberId = like.memberId,
            productId = like.productId,
        )
    }
}
