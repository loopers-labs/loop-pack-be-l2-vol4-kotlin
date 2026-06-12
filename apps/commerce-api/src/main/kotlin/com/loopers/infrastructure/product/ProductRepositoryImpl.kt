package com.loopers.infrastructure.product

import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductSort
import org.springframework.data.domain.Sort
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

    override fun findActiveAll(brandId: Long?, sort: ProductSort): List<ProductModel> {
        val jpaSort = when (sort) {
            ProductSort.LATEST -> Sort.by(Sort.Direction.DESC, "id")
            ProductSort.PRICE_ASC -> Sort.by(Sort.Order.asc("price"), Sort.Order.asc("id"))
            ProductSort.LIKES_DESC -> Sort.by(Sort.Order.desc("likeCount"), Sort.Order.desc("id"))
        }

        return if (brandId == null) {
            productJpaRepository.findAllByDeletedAtIsNull(jpaSort)
        } else {
            productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(brandId = brandId, sort = jpaSort)
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
