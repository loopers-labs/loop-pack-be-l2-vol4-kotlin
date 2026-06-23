package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort

enum class ProductSort {
    LATEST,
    PRICE_ASC,
    LIKES_DESC,
    ;

    fun toPageable(page: Int, size: Int): Pageable {
        val sort = when (this) {
            LATEST -> Sort.by(Sort.Direction.DESC, "id")
            PRICE_ASC -> Sort.by(Sort.Order.asc("price"), Sort.Order.asc("id"))
            LIKES_DESC -> Sort.by(Sort.Order.desc("likeCount"), Sort.Order.desc("id"))
        }
        return PageRequest.of(page, size, sort)
    }

    companion object {
        fun from(value: String?): ProductSort {
            if (value.isNullOrBlank()) return LATEST
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: throw CoreException(ErrorType.BAD_REQUEST, "지원하지 않는 상품 정렬 조건입니다.")
        }
    }
}
