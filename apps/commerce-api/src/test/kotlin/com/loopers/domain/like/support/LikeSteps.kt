package com.loopers.domain.like.support

import com.loopers.domain.like.model.LikeModel

class LikeSteps {
    companion object {
        const val 기본_사용자_ID: Long = 1L
        const val 기본_상품_ID: Long = 1L

        fun 좋아요_도메인_생성(
            userId: Long = 기본_사용자_ID,
            productId: Long = 기본_상품_ID,
        ): LikeModel = LikeModel.of(
            userId = userId,
            productId = productId,
        )
    }
}
