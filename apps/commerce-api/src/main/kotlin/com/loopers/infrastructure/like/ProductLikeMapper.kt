package com.loopers.infrastructure.like

import com.loopers.domain.like.Like

object ProductLikeMapper {
    fun toDomain(entity: ProductLikeEntity): Like {
        return Like(
            memberId = entity.memberId,
            productId = entity.productId,
        )
    }

    fun toEntity(like: Like): ProductLikeEntity {
        return ProductLikeEntity(
            memberId = like.memberId,
            productId = like.productId,
        )
    }
}
