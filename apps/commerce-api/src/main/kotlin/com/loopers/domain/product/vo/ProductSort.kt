package com.loopers.domain.product.vo

import com.loopers.domain.product.constant.ProductErrorMessages
import com.loopers.domain.product.exception.InvalidProductException

enum class ProductSort(
    val code: String,
) {
    LATEST("latest"),
    PRICE_ASC("price_asc"),
    LIKES_DESC("likes_desc"),
    ;

    companion object {
        fun fromCode(code: String?): ProductSort {
            val normalized = code
                ?.trim()
                ?.lowercase()
                ?.takeIf { it.isNotBlank() }
                ?: LATEST.code

            return entries.firstOrNull { it.code == normalized }
                ?: throw InvalidProductException(ProductErrorMessages.UNSUPPORTED_PRODUCT_SORT)
        }
    }
}
