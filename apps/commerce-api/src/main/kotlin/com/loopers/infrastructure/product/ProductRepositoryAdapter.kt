package com.loopers.infrastructure.product

import com.fasterxml.jackson.core.type.TypeReference
import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepositoryPort
import com.loopers.domain.product.ProductSort
import com.loopers.infrastructure.cache.CacheKeys
import com.loopers.infrastructure.cache.ProductCacheModel
import com.loopers.infrastructure.cache.ProductPageCacheModel
import com.loopers.infrastructure.cache.RedisCacheStore
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.data.domain.PageRequest as SpringPageRequest

/**
 * 캐싱은 이 어댑터 내부에서만 처리한다. 상위 계층은 캐시의 존재를 모른다.
 *
 * - [findById]: 정적인 상품 단건을 read-through 캐시. (변동성 큰 stock/likeCount 는 각자
 *   어댑터에서 캐싱하지 않아 항상 실시간 → "변동 필드 분리"가 상위 코드 변경 없이 성립)
 * - [findAll]: 좋아요순 + 특정 브랜드 페이지만 짧은 TTL 캐시.
 * - [save]/[delete]: 트랜잭션 커밋 이후 무효화(롤백 시 멀쩡한 새 값이 재적재되는 것을 방지).
 */
@Component
class ProductRepositoryAdapter(
    private val productJpaRepository: ProductJpaRepository,
    private val cacheStore: RedisCacheStore,
) : ProductRepositoryPort {
    override fun findById(id: Long): Product? {
        val key = CacheKeys.product(id)
        cacheStore.get(key, CacheKeys.PRODUCT_ZSET, PRODUCT_TYPE)?.let { return it.toDomain() }

        val product = productJpaRepository.findById(id).map { it.toDomain() }.orElse(null)
            ?: return null
        cacheStore.putWithLimit(
            key = key,
            value = ProductCacheModel.from(product),
            ttl = CacheKeys.productTtl(),
            zsetKey = CacheKeys.PRODUCT_ZSET,
            limit = CacheKeys.PRODUCT_LIMIT,
        )
        return product
    }

    override fun findAllByIds(ids: List<Long>): List<Product> {
        if (ids.isEmpty()) return emptyList()
        return productJpaRepository.findAllById(ids).map { it.toDomain() }
    }

    override fun findAll(brandId: Long?, sort: ProductSort, pageRequest: PageRequest): PageResult<Product> {
        if (sort == ProductSort.LIKES_DESC && brandId != null) {
            return findLikeDescPageCached(brandId, pageRequest)
        }
        return queryAll(brandId, sort, pageRequest)
    }

    private fun findLikeDescPageCached(brandId: Long, pageRequest: PageRequest): PageResult<Product> {
        val key = CacheKeys.likeDescPage(brandId, pageRequest.page, pageRequest.size)
        cacheStore.get(key, CacheKeys.PAGE_ZSET, PAGE_TYPE)?.let { return it.toDomain() }

        val result = queryAll(brandId, ProductSort.LIKES_DESC, pageRequest)
        cacheStore.putWithLimit(
            key = key,
            value = ProductPageCacheModel.from(result),
            ttl = CacheKeys.pageTtl(),
            zsetKey = CacheKeys.PAGE_ZSET,
            limit = CacheKeys.PAGE_LIMIT,
        )
        return result
    }

    private fun queryAll(brandId: Long?, sort: ProductSort, pageRequest: PageRequest): PageResult<Product> {
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
        val saved = productJpaRepository.save(entity).toDomain()
        evictProductAfterCommit(saved.id)
        return saved
    }

    override fun delete(product: Product) {
        if (product.id == 0L) {
            throw CoreException(ErrorType.BAD_REQUEST, "ID가 없는 상품은 삭제할 수 없습니다.")
        }
        val entity = productJpaRepository.findById(product.id)
            .orElseThrow { CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.") }
        entity.delete()
        productJpaRepository.save(entity)
        evictProductAfterCommit(product.id)
    }

    /** 트랜잭션이 활성화돼 있으면 커밋 이후 evict, 아니면 즉시 evict. */
    private fun evictProductAfterCommit(id: Long) {
        val key = CacheKeys.product(id)
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() {
                        cacheStore.evict(key, CacheKeys.PRODUCT_ZSET)
                    }
                },
            )
        } else {
            cacheStore.evict(key, CacheKeys.PRODUCT_ZSET)
        }
    }

    companion object {
        private val PRODUCT_TYPE = object : TypeReference<ProductCacheModel>() {}
        private val PAGE_TYPE = object : TypeReference<ProductPageCacheModel>() {}
    }
}
