package com.loopers.domain.product

import com.loopers.domain.brand.BrandRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProductCatalogService(
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
) {
    @Transactional(readOnly = true)
    fun getDetail(productId: Long): ProductDetail {
        val product = productRepository.findActiveById(productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.")
        val brand = brandRepository.findActiveById(product.brandId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.")

        return ProductDetail(product = product, brand = brand)
    }

    @Transactional(readOnly = true)
    fun getProducts(query: ProductQuery): List<ProductDetail> {
        val products = productRepository.findActiveAll(brandId = query.brandId, sort = query.sort)
        val brandsById = products
            .map { it.brandId }
            .distinct()
            .associateWith { brandId ->
                brandRepository.findActiveById(brandId)
                    ?: throw CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.")
            }

        return products.map { product ->
            ProductDetail(product = product, brand = brandsById.getValue(product.brandId))
        }
    }

    data class ProductQuery(
        val brandId: Long? = null,
        val sort: ProductSort = ProductSort.LATEST,
    )
}
