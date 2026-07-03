package com.loopers.application.product

import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandService
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.ProductSort
import com.loopers.domain.product.ProductViewedEvent
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

@Component
class ProductFacade(
    private val productService: ProductService,
    private val brandService: BrandService,
    private val productCacheStore: ProductCacheStore,
    private val eventPublisher: ApplicationEventPublisher,
) {
    fun getProductDetail(productId: Long): ProductInfo {
        val productInfo = productCacheStore.getProductDetail(productId) ?: run {
            val product = productService.getOnSaleById(productId)
            val brand = brandService.getById(product.brandId)
            ProductInfo.from(product, brand).also { productCacheStore.setProductDetail(productId, it) }
        }
        eventPublisher.publishEvent(ProductViewedEvent(productId = productId))
        return productInfo
    }

    fun getProducts(brandId: Long?, sort: ProductSort, pageable: Pageable): Page<ProductInfo> {
        productCacheStore.getProductList(brandId, sort, pageable)?.let {
            return PageImpl(it.content, pageable, it.totalElements)
        }

        val productsPage = productService.getOnSaleProducts(brandId, sort, pageable)
        val productInfoPage = mapToProductInfoPage(productsPage)
        productCacheStore.setProductList(
            brandId,
            sort,
            pageable,
            CachedProductPage(content = productInfoPage.content, totalElements = productInfoPage.totalElements),
        )
        return productInfoPage
    }

    private fun mapToProductInfoPage(productsPage: Page<ProductModel>): Page<ProductInfo> {
        val brandIds = productsPage.content.map { it.brandId }.distinct()
        val brandsById: Map<Long, BrandModel> = brandService.findAllActiveByIds(brandIds).associateBy { it.id }
        return productsPage.map { product ->
            val brand = brandsById[product.brandId]
                ?: throw CoreException(ErrorType.INTERNAL_ERROR, "상품(${product.id}) 의 브랜드(${product.brandId}) 가 존재하지 않습니다.")
            ProductInfo.from(product, brand)
        }
    }
}
