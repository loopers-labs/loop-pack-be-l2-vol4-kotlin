package com.loopers.infrastructure.brand

import com.fasterxml.jackson.core.type.TypeReference
import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandRepositoryPort
import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult
import com.loopers.infrastructure.cache.BrandCacheModel
import com.loopers.infrastructure.cache.CacheKeys
import com.loopers.infrastructure.cache.RedisCacheStore
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.PageRequest as SpringPageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * findById 는 정적인 브랜드 단건을 read-through 캐시한다(상품 상세 조립 시 반복 조회됨).
 * save/delete 시 트랜잭션 커밋 이후 무효화한다. 캐싱은 이 어댑터 내부 구현으로, 상위 계층은 모른다.
 */
@Component
class BrandRepositoryAdapter(
    private val brandJpaRepository: BrandJpaRepository,
    private val cacheStore: RedisCacheStore,
) : BrandRepositoryPort {
    override fun findById(id: Long): Brand? {
        val key = CacheKeys.brand(id)
        cacheStore.get(key, CacheKeys.BRAND_ZSET, BRAND_TYPE)?.let { return it.toDomain() }

        val brand = brandJpaRepository.findById(id).map { it.toDomain() }.orElse(null)
            ?: return null
        cacheStore.putWithLimit(
            key = key,
            value = BrandCacheModel.from(brand),
            ttl = CacheKeys.brandTtl(),
            zsetKey = CacheKeys.BRAND_ZSET,
            limit = CacheKeys.BRAND_LIMIT,
        )
        return brand
    }

    override fun findAllByIds(ids: List<Long>): List<Brand> {
        if (ids.isEmpty()) return emptyList()
        return brandJpaRepository.findAllById(ids).map { it.toDomain() }
    }

    override fun findAll(pageRequest: PageRequest): PageResult<Brand> {
        val springPageable = SpringPageRequest.of(pageRequest.page, pageRequest.size, Sort.by(Sort.Direction.ASC, "id"))
        val page = brandJpaRepository.findAll(springPageable)
        return PageResult.of(
            items = page.content.map { it.toDomain() },
            pageRequest = pageRequest,
            totalElements = page.totalElements,
        )
    }

    override fun existsByName(name: String): Boolean =
        brandJpaRepository.existsByName(name)

    override fun existsByNameAndIdNot(name: String, excludeId: Long): Boolean =
        brandJpaRepository.existsByNameAndIdNot(name, excludeId)

    override fun save(brand: Brand): Brand {
        val entity = if (brand.id == 0L) {
            BrandEntity.from(brand)
        } else {
            brandJpaRepository.findById(brand.id)
                .orElseThrow { CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.") }
                .apply { update(name = brand.name, description = brand.description) }
        }
        val saved = brandJpaRepository.save(entity).toDomain()
        evictBrandAfterCommit(saved.id)
        return saved
    }

    override fun delete(brand: Brand) {
        if (brand.id == 0L) {
            throw CoreException(ErrorType.BAD_REQUEST, "ID가 없는 브랜드는 삭제할 수 없습니다.")
        }
        val entity = brandJpaRepository.findById(brand.id)
            .orElseThrow { CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.") }
        entity.delete()
        brandJpaRepository.save(entity)
        evictBrandAfterCommit(brand.id)
    }

    private fun evictBrandAfterCommit(id: Long) {
        val key = CacheKeys.brand(id)
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() {
                        cacheStore.evict(key, CacheKeys.BRAND_ZSET)
                    }
                },
            )
        } else {
            cacheStore.evict(key, CacheKeys.BRAND_ZSET)
        }
    }

    companion object {
        private val BRAND_TYPE = object : TypeReference<BrandCacheModel>() {}
    }
}
