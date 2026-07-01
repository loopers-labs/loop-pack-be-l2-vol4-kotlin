package com.loopers.application.product

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.config.redis.RedisConfig
import com.loopers.domain.product.ProductSearchCondition
import com.loopers.support.paging.PageResult
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class ProductCacheService(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    @Value("\${product.cache.enabled:true}")
    private val enabled: Boolean,
    @Value("\${product.cache.ttl-seconds:30}")
    private val ttlSeconds: Long,
) {
    fun getProductDetail(productId: Long): ProductInfo? {
        if (!enabled) return null
        return get(detailKey(productId), productInfoType)
    }

    fun setProductDetail(productId: Long, info: ProductInfo) {
        if (!enabled) return
        set(detailKey(productId), info)
    }

    fun evictProductDetail(productId: Long) {
        if (!enabled) return
        runCatching {
            redisTemplate.delete(detailKey(productId))
        }.onFailure { exception ->
            log.warn("상품 상세 캐시 삭제 실패. productId={}", productId, exception)
        }
    }

    fun getProductList(condition: ProductSearchCondition): PageResult<ProductSummaryInfo>? {
        if (!enabled) return null
        return get(listKey(condition), productSummaryPageType)
    }

    fun setProductList(condition: ProductSearchCondition, result: PageResult<ProductSummaryInfo>) {
        if (!enabled) return
        set(listKey(condition), result)
    }

    private fun <T> get(key: String, typeReference: TypeReference<T>): T? {
        return runCatching {
            redisTemplate.opsForValue().get(key)
                ?.let { objectMapper.readValue(it, typeReference) }
        }.onFailure { exception ->
            log.warn("상품 캐시 조회 실패. key={}", key, exception)
        }.getOrNull()
    }

    private fun set(key: String, value: Any) {
        runCatching {
            val json = objectMapper.writeValueAsString(value)
            redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(ttlSeconds))
        }.onFailure { exception ->
            log.warn("상품 캐시 저장 실패. key={}", key, exception)
        }
    }

    private fun detailKey(productId: Long): String = "$DETAIL_KEY_PREFIX:$productId"

    private fun listKey(condition: ProductSearchCondition): String {
        val brandId = condition.brandId?.toString() ?: NULL_BRAND_ID
        val pageCondition = condition.pageCondition
        return "$LIST_KEY_PREFIX:$brandId:${condition.sortType}:${pageCondition.page}:${pageCondition.size}"
    }

    companion object {
        private val log = LoggerFactory.getLogger(ProductCacheService::class.java)
        private const val KEY_VERSION = "v1"
        private const val NULL_BRAND_ID = "_"
        private const val DETAIL_KEY_PREFIX = "product:detail:$KEY_VERSION"
        private const val LIST_KEY_PREFIX = "product:list:$KEY_VERSION"

        private val productInfoType = object : TypeReference<ProductInfo>() {}
        private val productSummaryPageType = object : TypeReference<PageResult<ProductSummaryInfo>>() {}
    }
}
