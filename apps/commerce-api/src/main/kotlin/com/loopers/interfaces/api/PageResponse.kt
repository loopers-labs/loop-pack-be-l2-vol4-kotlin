package com.loopers.interfaces.api

import com.loopers.support.page.PageResult

/**
 * 페이지네이션 응답 모델. 페이지 메타데이터(총 개수/총 페이지/다음 페이지 여부)를 함께 노출한다.
 */
data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
) {
    companion object {
        fun <T> from(result: PageResult<T>): PageResponse<T> = PageResponse(
            content = result.content,
            page = result.page,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            hasNext = result.hasNext,
        )
    }
}
