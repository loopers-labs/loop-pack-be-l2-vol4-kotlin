package com.loopers.infrastructure.catalog

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.loopers.application.catalog.CatalogInfo
import com.loopers.application.catalog.ProductSort
import com.loopers.application.catalog.port.CatalogProductQueryPort
import com.loopers.config.redis.InMemoryRedisTemplate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class CachedCatalogProductQueryPortTest {
    private val redisTemplate = InMemoryRedisTemplate()
    private val objectMapper = ObjectMapper().registerKotlinModule()

    @DisplayName("상품 목록은 상품 item 캐시를 사용하지 않고 원본 조회 결과를 반환한다.")
    @Test
    fun doesNotCacheProductItemsForListLookup() {
        val delegate = FakeCatalogProductQueryPort()
        val cache = RedisCatalogProductCache(redisTemplate, objectMapper)
        cache.putProduct(productRows.getValue(1L).copy(productName = "Stale Air Max"))
        val cachedPort = CachedCatalogProductQueryPort(delegate, cache)

        val result = cachedPort.findDisplayableProducts(ProductSort.LATEST, page = 0, size = 20)

        assertThat(result).containsExactlyElementsOf(productRows.values)
        assertThat(delegate.listCalls).isEqualTo(1)
        assertThat(delegate.itemCalls).isZero()
    }

    @DisplayName("브랜드 상품 목록은 상품 item 캐시를 사용하지 않고 원본 조회 결과를 반환한다.")
    @Test
    fun doesNotCacheProductItemsForBrandListLookup() {
        val delegate = FakeCatalogProductQueryPort()
        val cache = RedisCatalogProductCache(redisTemplate, objectMapper)
        cache.putProduct(productRows.getValue(1L).copy(productName = "Stale Air Max"))
        val cachedPort = CachedCatalogProductQueryPort(delegate, cache)

        val result = cachedPort.findDisplayableProductsByBrandId(brandId = 10L, ProductSort.LATEST, page = 0, size = 20)

        assertThat(result).containsExactlyElementsOf(productRows.values)
        assertThat(delegate.brandListCalls).isEqualTo(1)
        assertThat(delegate.itemCalls).isZero()
    }

    @DisplayName("상품 상세는 상품 item 과 상세 이미지를 캐시에서 재사용한다.")
    @Test
    fun cachesProductDetailByProductId() {
        val delegate = FakeCatalogProductQueryPort()
        val cache = RedisCatalogProductCache(redisTemplate, objectMapper)
        val cachedPort = CachedCatalogProductQueryPort(delegate, cache)

        val first = cachedPort.findDisplayableProductDetail(1L)
        val second = cachedPort.findDisplayableProductDetail(1L)

        assertThat(first).isEqualTo(second)
        assertThat(delegate.itemCalls).isEqualTo(1)
        assertThat(delegate.detailImageCalls).isEqualTo(1)
    }

    private class FakeCatalogProductQueryPort : CatalogProductQueryPort {
        var listCalls = 0
        var brandListCalls = 0
        var itemCalls = 0
        var detailImageCalls = 0

        override fun findDisplayableProducts(sort: ProductSort, page: Int, size: Int): List<CatalogInfo.ProductDisplayRow> {
            listCalls += 1
            return productRows.values.toList()
        }

        override fun findDisplayableProductsByBrandId(
            brandId: Long,
            sort: ProductSort,
            page: Int,
            size: Int,
        ): List<CatalogInfo.ProductDisplayRow> {
            brandListCalls += 1
            return productRows.values.filter { it.brandId == brandId }
        }

        override fun findDisplayableProduct(productId: Long): CatalogInfo.ProductDisplayRow? {
            itemCalls += 1
            return productRows[productId]
        }

        override fun findProductDetailImages(productId: Long): List<String> {
            detailImageCalls += 1
            return listOf("https://cdn.example.com/products/$productId/main.png")
        }

        override fun findDisplayableProductDetail(productId: Long): CatalogInfo.ProductDetailRow? =
            findDisplayableProduct(productId)?.let { product ->
                CatalogInfo.ProductDetailRow(product, findProductDetailImages(productId))
            }
    }
}

private val productRows = mapOf(
    1L to CatalogInfo.ProductDisplayRow(1L, "Air Max", 10L, "Nike", 129000, 5, 10, 0),
    2L to CatalogInfo.ProductDisplayRow(2L, "Dunk Low", 10L, "Nike", 99000, 3, 5, 1),
)
