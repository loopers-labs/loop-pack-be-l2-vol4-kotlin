package com.loopers.infrastructure.catalog

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.loopers.application.catalog.CatalogInfo
import com.loopers.application.catalog.port.CatalogProductCacheInvalidationPort
import com.loopers.config.redis.RedisConfig
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class RedisCatalogProductCache(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) : CatalogProductCacheInvalidationPort {
    private val ttl: Duration = Duration.ofMinutes(5)

    fun getProduct(productId: Long): CatalogInfo.ProductDisplayRow? =
        redisTemplate.opsForValue().get(productKey(productId))
            ?.let { objectMapper.readValue<CachedProductDisplayRow>(it).toInfo() }

    fun putProduct(product: CatalogInfo.ProductDisplayRow) {
        redisTemplate.opsForValue().set(
            productKey(product.productId),
            objectMapper.writeValueAsString(CachedProductDisplayRow.from(product)),
            ttl,
        )
        redisTemplate.opsForSet().add(brandProductsKey(product.brandId), product.productId.toString())
        redisTemplate.expire(brandProductsKey(product.brandId), ttl)
    }

    fun getDetailImages(productId: Long): List<String>? =
        redisTemplate.opsForValue().get(detailImagesKey(productId))
            ?.let { objectMapper.readValue<List<String>>(it) }

    fun putDetailImages(productId: Long, detailImages: List<String>) {
        redisTemplate.opsForValue().set(detailImagesKey(productId), objectMapper.writeValueAsString(detailImages), ttl)
    }

    override fun evictProduct(productId: Long) {
        redisTemplate.delete(listOf(productKey(productId), detailImagesKey(productId)))
    }

    override fun evictBrandProducts(brandId: Long) {
        val key = brandProductsKey(brandId)
        redisTemplate.opsForSet().members(key).orEmpty()
            .mapNotNull(String::toLongOrNull)
            .forEach(::evictProduct)
        redisTemplate.delete(key)
    }

    private fun productKey(productId: Long): String =
        "catalog:product:item:v1:$productId"

    private fun detailImagesKey(productId: Long): String =
        "catalog:product:detail-images:v1:$productId"

    private fun brandProductsKey(brandId: Long): String =
        "catalog:brand-products:v1:$brandId"

    private data class CachedProductDisplayRow(
        val productId: Long,
        val productName: String,
        val brandId: Long,
        val brandName: String,
        val price: Long,
        val likeCount: Long,
        val stockQuantity: Int,
        val reservedQuantity: Int,
    ) {
        fun toInfo() = CatalogInfo.ProductDisplayRow(
            productId = productId,
            productName = productName,
            brandId = brandId,
            brandName = brandName,
            price = price,
            likeCount = likeCount,
            stockQuantity = stockQuantity,
            reservedQuantity = reservedQuantity,
        )

        companion object {
            fun from(row: CatalogInfo.ProductDisplayRow) = CachedProductDisplayRow(
                productId = row.productId,
                productName = row.productName,
                brandId = row.brandId,
                brandName = row.brandName,
                price = row.price,
                likeCount = row.likeCount,
                stockQuantity = row.stockQuantity,
                reservedQuantity = row.reservedQuantity,
            )
        }
    }
}
