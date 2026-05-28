package com.loopers.interfaces.api

import org.springframework.data.domain.Page

data class PageResponse<T>(
    val data: List<T>,
    val meta: Metadata,
) {
    data class Metadata(
        val page: Int,
        val size: Int,
        val totalElements: Long,
        val totalPages: Int,
    )

    companion object {
        fun <T> from(page: Page<T>): PageResponse<T> {
            return PageResponse(
                data = page.content,
                meta = Metadata(
                    page = page.number,
                    size = page.size,
                    totalElements = page.totalElements,
                    totalPages = page.totalPages,
                ),
            )
        }
    }
}
