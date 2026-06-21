package com.loopers.infrastructure.cache

import com.loopers.domain.common.PageResult
import com.loopers.domain.product.Product

/**
 * 페이지 캐시 전용 DTO. `PageResult<Product>` 를 캐시에 담기 위한 표현.
 *
 * 역직렬화 시 제네릭 type erasure 때문에 반드시
 * `TypeReference<ProductPageCacheModel>` 로 복원해야 한다.
 */
data class ProductPageCacheModel(
    val items: List<ProductCacheModel>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
) {
    fun toDomain(): PageResult<Product> = PageResult(
        items = items.map { it.toDomain() },
        page = page,
        size = size,
        totalElements = totalElements,
        totalPages = totalPages,
    )

    companion object {
        fun from(result: PageResult<Product>): ProductPageCacheModel = ProductPageCacheModel(
            items = result.items.map { ProductCacheModel.from(it) },
            page = result.page,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
        )
    }
}
