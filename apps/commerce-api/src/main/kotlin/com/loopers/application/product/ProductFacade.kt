package com.loopers.application.product

import com.loopers.application.brand.BrandService
import com.loopers.application.product.dto.ProductDetailInfo
import com.loopers.application.product.dto.ProductListCommand
import com.loopers.application.productstat.ProductStatService
import com.loopers.domain.product.ProductCatalogService
import com.loopers.domain.product.dto.ProductSummary
import org.springframework.data.domain.Page
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProductFacade(
    private val productService: ProductService,
    private val brandService: BrandService,
    private val productStatService: ProductStatService,
    private val productCatalogService: ProductCatalogService,
) {
    @Transactional(readOnly = true)
    fun getProducts(command: ProductListCommand): Page<ProductSummary> {
        return productService.getProducts(command)
    }

    @Transactional(readOnly = true)
    fun getProduct(productId: Long): ProductDetailInfo {
        val product = productService.getProduct(productId)
        val brand = brandService.getBrand(product.brandId)
        val productStat = productStatService.getProductStat(product.id)
        val productCatalog = productCatalogService.display(
            product = product,
            brand = brand,
            productStat = productStat,
        )

        return ProductDetailInfo.from(productCatalog)
    }
}
