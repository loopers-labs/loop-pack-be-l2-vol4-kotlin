package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

enum class ProductSort(val value: String) {
    LATEST("latest"),
    PRICE_ASC("price_asc"),
    LIKES_DESC("likes_desc"),
    ;

    companion object {
        fun from(value: String?): ProductSort {
            if (value.isNullOrBlank()) {
                return LATEST
            }

            return entries.find { it.value == value }
                ?: throw CoreException(ErrorType.BAD_REQUEST, "Unsupported product sort: $value")
        }
    }
}
