package com.loopers.support.page

/**
 * 도메인 계층에서 사용하는 페이지 결과 모델.
 * Spring Data의 Page/Pageable에 직접 의존하지 않도록 페이지 메타데이터만 보관한다.
 */
data class PageResult<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
) {
    val totalPages: Int = if (size <= 0) 0 else ((totalElements + size - 1) / size).toInt()

    val hasNext: Boolean = page.toLong() * size + content.size < totalElements

    fun <R> map(transform: (T) -> R): PageResult<R> = PageResult(
        content = content.map(transform),
        page = page,
        size = size,
        totalElements = totalElements,
    )
}
