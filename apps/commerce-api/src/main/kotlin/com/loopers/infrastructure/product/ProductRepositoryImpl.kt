package com.loopers.infrastructure.product

import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductSort
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

@Component
class ProductRepositoryImpl(
    private val productJpaRepository: ProductJpaRepository,
) : ProductRepository {
    override fun save(product: ProductModel): ProductModel {
        return productJpaRepository.save(product)
    }

    override fun findActiveById(id: Long): ProductModel? {
        return productJpaRepository.findByIdAndDeletedAtIsNull(id)
    }

    override fun findActiveAll(brandId: Long?, sort: ProductSort, pageable: Pageable): Page<ProductModel> {
        val sortedPageable = sort.toPageable(page = pageable.pageNumber, size = pageable.pageSize)
        return if (brandId == null) {
            productJpaRepository.findAllByDeletedAtIsNull(sortedPageable)
        } else {
            productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(brandId = brandId, pageable = sortedPageable)
        }
    }

    override fun existsActiveById(id: Long): Boolean {
        return productJpaRepository.existsByIdAndDeletedAtIsNull(id)
    }

    override fun incrementLikeCount(productId: Long) {
        productJpaRepository.incrementLikeCount(productId)
    }

    override fun decrementLikeCount(productId: Long) {
        productJpaRepository.decrementLikeCount(productId)
    }
}
