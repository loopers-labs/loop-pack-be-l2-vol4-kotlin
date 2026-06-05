package com.loopers.domain.like.model

import com.loopers.domain.like.exception.InvalidLikeException

class LikeModel private constructor(
    val userId: Long,
    val productId: Long,
) {
    companion object {
        fun of(
            userId: Long,
            productId: Long,
        ): LikeModel {
            validatePositive("사용자 ID", userId)
            validatePositive("상품 ID", productId)
            return LikeModel(
                userId = userId,
                productId = productId,
            )
        }

        private fun validatePositive(
            label: String,
            value: Long,
        ) {
            if (value <= 0) {
                throw InvalidLikeException("$label 는 양수여야 합니다.")
            }
        }
    }
}
