package com.loopers.domain.productstat

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

class ProductStat(
    val id: Long = 0L,
    val productId: Long,
    likeCount: Long,
) {
    var likeCount: Long = likeCount
        private set

    init {
        if (productId <= 0L) {
            throw CoreException(ErrorType.BAD_REQUEST, "Product id must be positive.")
        }
        if (likeCount < 0L) {
            throw CoreException(ErrorType.BAD_REQUEST, "Like count must not be negative.")
        }
    }

    fun increaseLikeCount() {
        likeCount += 1
    }

    fun decreaseLikeCount() {
        if (likeCount == 0L) {
            throw CoreException(ErrorType.BAD_REQUEST, "Like count must not be negative.")
        }
        likeCount -= 1
    }
}
