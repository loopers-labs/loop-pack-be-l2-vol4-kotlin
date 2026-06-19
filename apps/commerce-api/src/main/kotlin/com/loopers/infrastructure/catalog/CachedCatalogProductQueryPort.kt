package com.loopers.infrastructure.catalog

import com.loopers.application.catalog.CatalogInfo
import com.loopers.application.catalog.ProductSort
import com.loopers.application.catalog.port.CatalogProductQueryPort
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

@Primary
@Component
class CachedCatalogProductQueryPort(
    @Qualifier("catalogProductQueryDao")
    private val delegate: CatalogProductQueryPort,
    private val cache: RedisCatalogProductCache,
) : CatalogProductQueryPort {
    override fun findDisplayableProducts(sort: ProductSort, page: Int, size: Int): List<CatalogInfo.ProductDisplayRow> =
        delegate.findDisplayableProducts(sort, page, size)

    override fun findDisplayableProductsByBrandId(
        brandId: Long,
        sort: ProductSort,
        page: Int,
        size: Int,
    ): List<CatalogInfo.ProductDisplayRow> =
        delegate.findDisplayableProductsByBrandId(brandId, sort, page, size)

    override fun findDisplayableProduct(productId: Long): CatalogInfo.ProductDisplayRow? =
        getOrPutProduct(productId) { delegate.findDisplayableProduct(productId) }

    override fun findProductDetailImages(productId: Long): List<String> =
        cache.getDetailImages(productId)
            ?: delegate.findProductDetailImages(productId)
                .also { cache.putDetailImages(productId, it) }

    override fun findDisplayableProductDetail(productId: Long): CatalogInfo.ProductDetailRow? =
        findDisplayableProduct(productId)?.let { product ->
            CatalogInfo.ProductDetailRow(product = product, detailImages = findProductDetailImages(productId))
        }

    private fun getOrPutProduct(
        productId: Long,
        loader: () -> CatalogInfo.ProductDisplayRow?,
    ): CatalogInfo.ProductDisplayRow? {
        val cached = cache.getProduct(productId)
        if (cached != null) return cached
        val product = loader() ?: return null
        cache.putProduct(product)
        return product
    }
}
