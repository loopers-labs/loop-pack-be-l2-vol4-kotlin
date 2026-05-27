package com.loopers.application.product

import com.loopers.domain.product.ProductCatalogService
import com.loopers.domain.product.ProductSort
import org.springframework.stereotype.Component

@Component
class ProductFacade(
    private val productCatalogService: ProductCatalogService,
) {
    fun getProductDetail(productId: Long): ProductInfo {
        return productCatalogService.getDetail(productId).let { ProductInfo.from(it) }
    }

    fun getProducts(query: ProductQuery): List<ProductInfo> {
        return productCatalogService.getProducts(
            ProductCatalogService.ProductQuery(
                brandId = query.brandId,
                sort = query.sort,
            ),
        ).map { ProductInfo.from(it) }
    }

    data class ProductQuery(
        val brandId: Long? = null,
        val sort: ProductSort = ProductSort.LATEST,
    )
}
