package com.loopers.infrastructure.product

import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductSort
import com.loopers.domain.product.dto.ProductSummary
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class ProductRepositoryImpl(
    private val productJpaRepository: ProductJpaRepository,
) : ProductRepository {
    override fun findById(productId: Long): Product? {
        return productJpaRepository.findByIdOrNull(productId)
            ?.let(ProductMapper::toDomain)
    }

    override fun findAllByBrandId(brandId: Long): List<Product> {
        return productJpaRepository.findAllByBrandId(brandId)
            .map(ProductMapper::toDomain)
    }

    override fun findDisplayableSummaries(
        brandId: Long?,
        sort: ProductSort,
        page: Int,
        size: Int,
    ): Page<ProductSummary> {
        val pageable = PageRequest.of(page, size)
        return productJpaRepository.findDisplayableSummaries(
            brandId = brandId,
            sort = sort,
            pageable = pageable,
        )
    }

    override fun save(product: Product): Product {
        val entity = if (product.id == 0L) {
            ProductMapper.toEntity(product)
        } else {
            productJpaRepository.findByIdOrNull(product.id)
                ?.also { it.update(product) }
                ?: throw CoreException(ErrorType.NOT_FOUND, "Product not found.")
        }

        return productJpaRepository.save(entity)
            .let(ProductMapper::toDomain)
    }

    override fun updateAll(products: Collection<Product>): List<Product> {
        if (products.isEmpty()) {
            return emptyList()
        }

        val productById = products.associateBy { it.id }
        val entities = productJpaRepository.findAllById(productById.keys)
            .onEach { entity ->
                productById[entity.id]?.let(entity::update)
            }

        return productJpaRepository.saveAll(entities)
            .map(ProductMapper::toDomain)
    }
}
