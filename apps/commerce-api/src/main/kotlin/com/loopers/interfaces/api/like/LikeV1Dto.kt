package com.loopers.interfaces.api.like

import com.loopers.application.like.ProductLikeInfo

class LikeV1Dto {
    data class LikeResponse(
        val productId: Long,
        val liked: Boolean,
    ) {
        companion object {
            fun from(info: ProductLikeInfo): LikeResponse =
                LikeResponse(productId = info.productId, liked = info.liked)
        }
    }
}
