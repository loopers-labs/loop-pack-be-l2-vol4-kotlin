package com.loopers.domain.common

data class PageResult<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
) {
    companion object {
        fun <T> of(items: List<T>, pageRequest: PageRequest, totalElements: Long): PageResult<T> {
            val totalPages = if (pageRequest.size == 0) 0 else ((totalElements + pageRequest.size - 1) / pageRequest.size).toInt()
            return PageResult(
                items = items,
                page = pageRequest.page,
                size = pageRequest.size,
                totalElements = totalElements,
                totalPages = totalPages,
            )
        }
    }
}
