package com.loopers.domain.product.model

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

class ProductStat(
    val id: Long = 0L,
    val productId: Long,
    val brandId: Long,
    likeCount: Long,
    salesCount: Long = 0L,
    viewCount: Long = 0L,
) {
    var likeCount: Long = likeCount
        private set
    var salesCount: Long = salesCount
        private set
    var viewCount: Long = viewCount
        private set

    init {
        if (productId <= 0L) {
            throw CoreException(ErrorType.BAD_REQUEST, "Product id must be positive.")
        }
        if (brandId <= 0L) {
            throw CoreException(ErrorType.BAD_REQUEST, "Brand id must be positive.")
        }
        if (likeCount < 0L) {
            throw CoreException(ErrorType.BAD_REQUEST, "Like count must not be negative.")
        }
        if (salesCount < 0L) {
            throw CoreException(ErrorType.BAD_REQUEST, "Sales count must not be negative.")
        }
        if (viewCount < 0L) {
            throw CoreException(ErrorType.BAD_REQUEST, "View count must not be negative.")
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

    companion object {
        fun empty(productId: Long, brandId: Long): ProductStat {
            return ProductStat(
                productId = productId,
                brandId = brandId,
                likeCount = 0L,
                salesCount = 0L,
                viewCount = 0L,
            )
        }
    }
}
