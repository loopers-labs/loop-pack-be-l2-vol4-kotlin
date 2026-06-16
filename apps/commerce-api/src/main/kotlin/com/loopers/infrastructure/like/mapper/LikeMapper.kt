package com.loopers.infrastructure.like.mapper

import com.loopers.domain.like.model.Like
import com.loopers.infrastructure.like.entity.LikeEntity

object LikeMapper {
    fun toDomain(entity: LikeEntity): Like {
        return Like(
            memberId = entity.memberId,
            productId = entity.productId,
        )
    }

    fun toEntity(like: Like): LikeEntity {
        return LikeEntity(
            memberId = like.memberId,
            productId = like.productId,
        )
    }
}
