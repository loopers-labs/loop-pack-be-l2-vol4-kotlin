package com.loopers.interfaces.api

import com.loopers.support.paging.PageResult

data class PageResponse<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
) {
    companion object {
        fun <T, R> from(result: PageResult<T>, transform: (T) -> R): PageResponse<R> =
            PageResponse(
                items = result.items.map(transform),
                page = result.page,
                size = result.size,
                totalElements = result.totalElements,
                totalPages = result.totalPages,
            )
    }
}
