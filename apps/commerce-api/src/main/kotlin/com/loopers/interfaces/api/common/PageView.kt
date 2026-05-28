package com.loopers.interfaces.api.common

import com.loopers.domain.common.PageResult

data class PageView<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
) {
    companion object {
        fun <S, T> from(result: PageResult<S>, mapper: (S) -> T): PageView<T> = PageView(
            items = result.items.map(mapper),
            page = result.page,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
        )
    }
}
