package com.loopers.application.catalog.port

import com.loopers.application.catalog.CatalogInfo
import com.loopers.application.catalog.ProductSort

interface CatalogProductQueryPort {
    fun findDisplayableProducts(sort: ProductSort, page: Int, size: Int): List<CatalogInfo.ProductDisplayRow>

    fun findDisplayableProductsByBrandId(
        brandId: Long,
        sort: ProductSort,
        page: Int,
        size: Int,
    ): List<CatalogInfo.ProductDisplayRow> =
        findDisplayableProducts(sort, page, size).filter { it.brandId == brandId }

    fun findDisplayableProduct(productId: Long): CatalogInfo.ProductDisplayRow? =
        findDisplayableProductDetail(productId)?.product

    fun findProductDetailImages(productId: Long): List<String> =
        findDisplayableProductDetail(productId)?.detailImages.orEmpty()

    fun findDisplayableProductDetail(productId: Long): CatalogInfo.ProductDetailRow?
}
