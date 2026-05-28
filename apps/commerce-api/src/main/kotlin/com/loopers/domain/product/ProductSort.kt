package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

enum class ProductSort(val raw: String) {
    LATEST("latest"),
    PRICE_ASC("price_asc"),
    LIKES_DESC("likes_desc"),
    ;

    companion object {
        fun from(raw: String?): ProductSort {
            if (raw.isNullOrBlank()) return LATEST
            return values().firstOrNull { it.raw == raw }
                ?: throw CoreException(ErrorType.BAD_REQUEST, "지원하지 않는 정렬 옵션입니다: $raw")
        }
    }
}
