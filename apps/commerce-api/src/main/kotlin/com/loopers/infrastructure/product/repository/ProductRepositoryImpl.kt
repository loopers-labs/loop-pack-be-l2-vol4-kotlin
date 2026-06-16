package com.loopers.infrastructure.product.repository

import com.loopers.domain.product.ProductSort
import com.loopers.domain.product.dto.ProductSummary
import com.loopers.domain.product.model.Product
import com.loopers.domain.product.repository.ProductRepository
import com.loopers.infrastructure.product.mapper.ProductMapper
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.dao.DataIntegrityViolationException
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

    override fun findAllByIds(productIds: Collection<Long>): List<Product> {
        if (productIds.isEmpty()) {
            return emptyList()
        }

        return productJpaRepository.findAllById(productIds)
            .map(ProductMapper::toDomain)
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

    override fun existsByBrandIdAndName(brandId: Long, name: String): Boolean {
        return productJpaRepository.existsByBrandIdAndName(brandId = brandId, name = name)
    }

    override fun existsByBrandIdAndNameAndIdNot(brandId: Long, name: String, productId: Long): Boolean {
        return productJpaRepository.existsByBrandIdAndNameAndIdNot(
            brandId = brandId,
            name = name,
            productId = productId,
        )
    }

    override fun save(product: Product): Product {
        return try {
            productJpaRepository.save(ProductMapper.toEntity(product))
                .let(ProductMapper::toDomain)
        } catch (e: DataIntegrityViolationException) {
            throw CoreException(ErrorType.CONFLICT, "Product name already exists in brand.")
        }
    }

    override fun update(product: Product): Product {
        val entity = productJpaRepository.findByIdOrNull(product.id)
            ?.also { it.update(product) }
            ?: throw CoreException(ErrorType.NOT_FOUND, "Product not found.")

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
