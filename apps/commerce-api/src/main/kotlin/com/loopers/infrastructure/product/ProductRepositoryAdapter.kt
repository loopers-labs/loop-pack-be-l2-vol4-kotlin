package com.loopers.infrastructure.product

import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepositoryPort
import com.loopers.domain.product.ProductSort
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import org.springframework.data.domain.PageRequest as SpringPageRequest

@Component
class ProductRepositoryAdapter(
    private val productJpaRepository: ProductJpaRepository,
) : ProductRepositoryPort {
    override fun findById(id: Long): Product? =
        productJpaRepository.findById(id).map { it.toDomain() }.orElse(null)

    override fun findAllByIds(ids: List<Long>): List<Product> {
        if (ids.isEmpty()) return emptyList()
        return productJpaRepository.findAllById(ids).map { it.toDomain() }
    }

    override fun findAll(brandId: Long?, sort: ProductSort, pageRequest: PageRequest): PageResult<Product> {
        val page: Page<ProductEntity> = when (sort) {
            ProductSort.LATEST -> {
                val pageable = SpringPageRequest.of(
                    pageRequest.page,
                    pageRequest.size,
                    Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")),
                )
                if (brandId == null) {
                    productJpaRepository.findAll(pageable)
                } else {
                    productJpaRepository.findAllByBrandId(brandId, pageable)
                }
            }
            ProductSort.PRICE_ASC -> {
                val pageable = SpringPageRequest.of(
                    pageRequest.page,
                    pageRequest.size,
                    Sort.by(Sort.Direction.ASC, "price").and(Sort.by(Sort.Direction.ASC, "id")),
                )
                if (brandId == null) {
                    productJpaRepository.findAll(pageable)
                } else {
                    productJpaRepository.findAllByBrandId(brandId, pageable)
                }
            }
            ProductSort.LIKES_DESC -> {
                val pageable = SpringPageRequest.of(pageRequest.page, pageRequest.size)
                productJpaRepository.findAllOrderByLikesDesc(brandId, pageable)
            }
        }
        return PageResult.of(
            items = page.content.map { it.toDomain() },
            pageRequest = pageRequest,
            totalElements = page.totalElements,
        )
    }

    override fun findAllByBrandId(brandId: Long): List<Product> =
        productJpaRepository.findAllByBrandId(brandId).map { it.toDomain() }

    override fun save(product: Product): Product {
        val entity = if (product.id == 0L) {
            ProductEntity.from(product)
        } else {
            productJpaRepository.findById(product.id)
                .orElseThrow { CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.") }
                .apply { update(name = product.name, price = product.price, description = product.description) }
        }
        return productJpaRepository.save(entity).toDomain()
    }

    override fun delete(product: Product) {
        if (product.id == 0L) {
            throw CoreException(ErrorType.BAD_REQUEST, "ID가 없는 상품은 삭제할 수 없습니다.")
        }
        val entity = productJpaRepository.findById(product.id)
            .orElseThrow { CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.") }
        entity.delete()
        productJpaRepository.save(entity)
    }
}
