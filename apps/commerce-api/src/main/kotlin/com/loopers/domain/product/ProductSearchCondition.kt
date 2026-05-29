package com.loopers.domain.product

import com.loopers.support.paging.PageCondition

data class ProductSearchCondition(
    val brandId: Long? = null,
    val sortType: ProductSortType = ProductSortType.LATEST,
    val pageCondition: PageCondition = PageCondition(),
)
