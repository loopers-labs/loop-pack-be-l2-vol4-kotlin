package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

enum class ProductSort {
    LATEST,
    PRICE_ASC,
    LIKES_DESC,
    ;

    companion object {
        fun from(value: String?): ProductSort {
            if (value.isNullOrBlank()) return LATEST
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: throw CoreException(ErrorType.BAD_REQUEST, "지원하지 않는 상품 정렬 조건입니다.")
        }
    }
}
