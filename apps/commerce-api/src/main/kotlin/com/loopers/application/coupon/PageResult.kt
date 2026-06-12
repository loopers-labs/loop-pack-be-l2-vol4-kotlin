package com.loopers.application.coupon

import org.springframework.data.domain.Page

data class PageResult<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalCount: Long,
) {
    companion object {
        fun <S, T> from(page: Page<S>, mapper: (S) -> T): PageResult<T> {
            return PageResult(
                items = page.content.map(mapper),
                page = page.number,
                size = page.size,
                totalCount = page.totalElements,
            )
        }
    }
}
