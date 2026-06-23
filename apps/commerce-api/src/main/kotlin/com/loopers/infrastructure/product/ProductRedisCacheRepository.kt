package com.loopers.infrastructure.product

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.product.ProductCacheRepository
import com.loopers.application.product.ProductInfo
import com.loopers.application.product.ProductPageInfo
import com.loopers.config.redis.RedisConfig
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class ProductRedisCacheRepository(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) : ProductCacheRepository {
    private val log = LoggerFactory.getLogger(ProductRedisCacheRepository::class.java)

    override fun getDetail(productId: Long): ProductInfo? {
        val key = detailKey(productId)
        return getValue(key)
            ?.let { readValueOrNull(key = key, value = it, type = ProductInfo::class.java) }
    }

    override fun putDetail(productId: Long, product: ProductInfo) {
        val key = detailKey(productId)
        runCatching {
            redisTemplate.opsForValue().set(
                key,
                objectMapper.writeValueAsString(product),
                DETAIL_TTL,
            )
        }.onFailure { log.warn("Failed to put product detail cache. key={}", key, it) }
    }

    override fun evictDetail(productId: Long) {
        val key = detailKey(productId)
        runCatching { redisTemplate.delete(key) }
            .onFailure { log.warn("Failed to evict product detail cache. key={}", key, it) }
    }

    override fun getList(query: ProductCacheRepository.ProductListCacheQuery): ProductPageInfo? {
        val key = listKey(query)
        return getValue(key)
            ?.let { readValueOrNull(key = key, value = it, type = ProductPageInfo::class.java) }
    }

    override fun putList(query: ProductCacheRepository.ProductListCacheQuery, products: ProductPageInfo) {
        val key = listKey(query)
        runCatching {
            redisTemplate.opsForValue().set(
                key,
                objectMapper.writeValueAsString(products),
                LIST_TTL,
            )
        }.onFailure { log.warn("Failed to put product list cache. key={}", key, it) }
    }

    private fun getValue(key: String): String? {
        return runCatching { redisTemplate.opsForValue().get(key) }
            .onFailure { log.warn("Failed to get product cache. key={}", key, it) }
            .getOrNull()
    }

    private fun <T> readValueOrNull(key: String, value: String, type: Class<T>): T? {
        return runCatching { objectMapper.readValue(value, type) }
            .onFailure {
                log.warn("Failed to deserialize product cache. key={}", key, it)
                evictKey(key)
            }
            .getOrNull()
    }

    private fun evictKey(key: String) {
        runCatching { redisTemplate.delete(key) }
            .onFailure { log.warn("Failed to evict product cache. key={}", key, it) }
    }

    companion object {
        val DETAIL_TTL: Duration = Duration.ofSeconds(60)
        val LIST_TTL: Duration = Duration.ofSeconds(30)

        fun detailKey(productId: Long): String {
            return "commerce-api:product:detail:v1:$productId"
        }

        fun listKey(query: ProductCacheRepository.ProductListCacheQuery): String {
            val brandKey = query.brandId?.toString() ?: "all"
            return "commerce-api:product:list:v1:brand:$brandKey:sort:${query.sort.name}:page:${query.page}:size:${query.size}"
        }
    }
}
