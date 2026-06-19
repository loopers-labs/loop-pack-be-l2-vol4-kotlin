package com.loopers.infrastructure.product.cache

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.loopers.application.product.cache.ProductCacheRepository
import com.loopers.application.product.dto.ProductDetailInfo
import com.loopers.application.product.dto.ProductListCommand
import com.loopers.domain.product.dto.ProductSummary
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class RedisProductCacheRepository(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) : ProductCacheRepository {
    override fun findDetail(productId: Long): ProductDetailInfo? {
        return redisTemplate.opsForValue()
            .get(detailKey(productId))
            ?.let { objectMapper.readValue<ProductDetailInfo>(it) }
    }

    override fun saveDetail(productId: Long, productDetail: ProductDetailInfo) {
        redisTemplate.opsForValue()
            .set(detailKey(productId), objectMapper.writeValueAsString(productDetail))
    }

    override fun evictDetail(productId: Long) {
        redisTemplate.delete(detailKey(productId))
    }

    override fun findList(command: ProductListCommand): Page<ProductSummary>? {
        return redisTemplate.opsForValue()
            .get(listKey(command))
            ?.let { objectMapper.readValue<ProductListCacheValue>(it) }
            ?.toPage()
    }

    override fun saveList(command: ProductListCommand, productSummaries: Page<ProductSummary>) {
        val cacheValue = ProductListCacheValue.from(productSummaries)
        redisTemplate.opsForValue()
            .set(listKey(command), objectMapper.writeValueAsString(cacheValue))
    }

    override fun acquireListRefreshLock(command: ProductListCommand): Boolean {
        return redisTemplate.opsForValue()
            .setIfAbsent(refreshLockKey(command), "1", REFRESH_LOCK_TTL) == true
    }

    override fun releaseListRefreshLock(command: ProductListCommand) {
        redisTemplate.delete(refreshLockKey(command))
    }

    private fun detailKey(productId: Long): String {
        return "product:detail:$productId"
    }

    private fun listKey(command: ProductListCommand): String {
        val brandId = command.brandId?.toString() ?: "all"
        return "product:list:brand:$brandId:sort:${command.sort.value}:page:${command.page}:size:${command.size}"
    }

    private fun refreshLockKey(command: ProductListCommand): String {
        return "product:list:refresh-lock:${listKey(command)}"
    }

    private companion object {
        private val REFRESH_LOCK_TTL: Duration = Duration.ofSeconds(30)
    }
}

data class ProductListCacheValue(
    val content: List<ProductSummary>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
) {
    fun toPage(): Page<ProductSummary> {
        return PageImpl(
            content,
            PageRequest.of(page, size),
            totalElements,
        )
    }

    companion object {
        fun from(page: Page<ProductSummary>): ProductListCacheValue {
            return ProductListCacheValue(
                content = page.content,
                page = page.number,
                size = page.size,
                totalElements = page.totalElements,
            )
        }
    }
}
