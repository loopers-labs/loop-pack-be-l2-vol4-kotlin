package com.loopers.domain.product.application.command

import com.loopers.domain.product.exception.InvalidProductException
import com.loopers.domain.product.vo.ProductSort
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

data class ProductSearchCommand(
    val brandId: Long? = null,
    val sort: ProductSort = ProductSort.LATEST,
    val page: Int = DEFAULT_PAGE,
    val size: Int = DEFAULT_SIZE,
) {
    init {
        validatePage(page)
        validateSize(size)
    }

    companion object {
        const val DEFAULT_PAGE = 0
        const val DEFAULT_SIZE = 20

        fun of(
            brandId: Long? = null,
            sort: String? = null,
            page: Int? = null,
            size: Int? = null,
        ): ProductSearchCommand {
            val productSort = try {
                ProductSort.fromCode(sort)
            } catch (e: InvalidProductException) {
                throw CoreException(ErrorType.BAD_REQUEST, e.message, e)
            }
            return ProductSearchCommand(
                brandId = brandId,
                sort = productSort,
                page = page ?: DEFAULT_PAGE,
                size = size ?: DEFAULT_SIZE,
            )
        }

        private fun validatePage(page: Int) {
            if (page < 0) {
                throw CoreException(ErrorType.BAD_REQUEST, "페이지 번호는 0 이상이어야 합니다.")
            }
        }

        private fun validateSize(size: Int) {
            if (size < 1) {
                throw CoreException(ErrorType.BAD_REQUEST, "페이지 크기는 1 이상이어야 합니다.")
            }
        }
    }
}
