package com.loopers.application.product

import com.loopers.application.brand.BrandApplicationService
import com.loopers.domain.product.ProductPrice
import com.loopers.domain.product.ProductSearchCondition
import com.loopers.domain.product.Stock
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.paging.PageResult
import org.springframework.stereotype.Component

@Component
class ProductFacade(
    private val productApplicationService: ProductApplicationService,
    private val brandApplicationService: BrandApplicationService,
) {
    fun createProduct(
        brandId: Long,
        name: String,
        description: String,
        price: Long,
        stock: Int,
    ): ProductInfo {
        brandApplicationService.getBrand(brandId)
        return productApplicationService.createProduct(
            brandId = brandId,
            name = name,
            description = description,
            price = ProductPrice(price),
            stock = Stock(stock),
        ).let { ProductInfo.from(it) }
    }

    fun getProductDetail(productId: Long): ProductDetailInfo {
        val product = productApplicationService.getProduct(productId)
        val brand = brandApplicationService.getBrand(product.brandId)
        return ProductDetailInfo(
            product = ProductInfo.from(product),
            brand = brand,
        )
    }

    fun getProducts(condition: ProductSearchCondition): PageResult<ProductSummaryInfo> {
        condition.brandId?.let { brandApplicationService.getBrand(it) }

        val products = productApplicationService.getProducts(condition)
        val brandIds = products.items.map { it.brandId }.distinct()
        val brandMap = brandApplicationService.getBrands(brandIds)
            .associateBy { it.id }

        return PageResult(
            items = products.items.map { product ->
                val brand = brandMap[product.brandId]
                    ?: throw CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다. id=${product.brandId}")
                ProductSummaryInfo.from(product, brand.name)
            },
            page = products.page,
            size = products.size,
            totalElements = products.totalElements,
            totalPages = products.totalPages,
        )
    }

    fun updateProduct(
        productId: Long,
        name: String,
        description: String,
        price: Long,
        stock: Int,
    ): ProductInfo {
        return productApplicationService.updateProduct(
            id = productId,
            name = name,
            description = description,
            price = ProductPrice(price),
            stock = Stock(stock),
        ).let { ProductInfo.from(it) }
    }

    fun deleteProduct(productId: Long) {
        productApplicationService.deleteProduct(productId)
    }
}
