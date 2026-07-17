package com.loopers.application.product

import com.loopers.domain.brand.BrandService
import com.loopers.domain.event.ProductViewedEvent
import com.loopers.domain.product.ProductDetailService
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.ProductSortType
import com.loopers.infrastructure.brand.BrandCacheManager
import com.loopers.infrastructure.product.ProductCacheInfo
import com.loopers.infrastructure.product.ProductCacheManager
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

@Component
class ProductFacade(
    private val productDetailService: ProductDetailService,
    private val productService: ProductService,
    private val brandService: BrandService,
    private val productCacheManager: ProductCacheManager,
    private val brandCacheManager: BrandCacheManager,
    private val eventPublisher: ApplicationEventPublisher,
) {

    fun getProductDetail(productId: Long): ProductDetailInfo {
        val detail = resolveProductDetail(productId)
        eventPublisher.publishEvent(ProductViewedEvent(userId = null, productId = productId))
        return detail
    }

    private fun resolveProductDetail(productId: Long): ProductDetailInfo {
        productCacheManager.getDetail(productId)?.let { return ProductDetailInfo.from(it) }

        val product = productService.getProduct(productId)
        val (brandId, brandName) = resolveBrand(product.brandId)

        val cacheInfo = ProductCacheInfo(
            id = product.id,
            name = product.name,
            price = product.price,
            stock = product.stock,
            brandId = brandId,
            brandName = brandName,
            likeCount = product.likeCount,
        )

        productCacheManager.putDetail(productId, cacheInfo)
        return ProductDetailInfo.from(cacheInfo)
    }

    fun getProducts(brandId: Long?, sortType: ProductSortType, pageable: Pageable): Page<ProductDetailInfo> {
        val productsWithBrands = productDetailService.getProductsWithBrands(brandId, sortType, pageable)
        return productsWithBrands.map { (product, brand) ->
            ProductDetailInfo.of(product, brand, product.likeCount)
        }
    }

    private fun resolveBrand(brandId: Long): Pair<Long, String> {
        brandCacheManager.getDetail(brandId)?.let { return it.id to it.name }
        val brand = brandService.getBrand(brandId)
        brandCacheManager.putDetail(brandId, brand)
        return brand.id to brand.name
    }
}
