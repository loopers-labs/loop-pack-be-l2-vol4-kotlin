package com.loopers.infrastructure.product

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.product.ProductCacheRepository
import com.loopers.application.product.ProductInfo
import com.loopers.application.product.ProductPageInfo
import com.loopers.config.redis.RedisConfig
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
    override fun getDetail(productId: Long): ProductInfo? {
        return redisTemplate.opsForValue().get(detailKey(productId))
            ?.let { objectMapper.readValue(it, ProductInfo::class.java) }
    }

    override fun putDetail(productId: Long, product: ProductInfo) {
        redisTemplate.opsForValue().set(
            detailKey(productId),
            objectMapper.writeValueAsString(product),
            DETAIL_TTL,
        )
    }

    override fun evictDetail(productId: Long) {
        redisTemplate.delete(detailKey(productId))
    }

    override fun getList(query: ProductCacheRepository.ProductListCacheQuery): ProductPageInfo? {
        return redisTemplate.opsForValue().get(listKey(query))
            ?.let { objectMapper.readValue(it, ProductPageInfo::class.java) }
    }

    override fun putList(query: ProductCacheRepository.ProductListCacheQuery, products: ProductPageInfo) {
        redisTemplate.opsForValue().set(
            listKey(query),
            objectMapper.writeValueAsString(products),
            LIST_TTL,
        )
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
