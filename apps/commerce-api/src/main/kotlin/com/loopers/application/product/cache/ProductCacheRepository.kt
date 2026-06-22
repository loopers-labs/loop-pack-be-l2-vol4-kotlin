package com.loopers.application.product.cache

import com.loopers.application.product.dto.ProductDetailInfo
import com.loopers.application.product.dto.ProductListCommand
import com.loopers.domain.product.dto.ProductSummary
import org.springframework.data.domain.Page

interface ProductCacheRepository {
    fun findDetail(productId: Long): ProductDetailInfo?

    fun saveDetail(productId: Long, productDetail: ProductDetailInfo)

    fun evictDetail(productId: Long)

    fun findList(command: ProductListCommand): Page<ProductSummary>?

    fun saveList(command: ProductListCommand, productSummaries: Page<ProductSummary>)

    fun acquireListRefreshLock(command: ProductListCommand): Boolean

    fun releaseListRefreshLock(command: ProductListCommand)
}
