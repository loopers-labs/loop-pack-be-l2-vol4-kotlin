package com.loopers.application.product.cache

import com.loopers.application.product.ProductService
import com.loopers.application.product.cache.ProductCacheTaskConfig.Companion.PRODUCT_CACHE_TASK_EXECUTOR
import com.loopers.application.product.dto.ProductDetailInfo
import com.loopers.application.product.dto.ProductListCommand
import com.loopers.domain.product.dto.ProductSummary
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.TaskExecutor
import org.springframework.data.domain.Page
import org.springframework.stereotype.Component

@Component
class ProductCacheService(
    private val productService: ProductService,
    private val productCacheRepository: ProductCacheRepository,
    @Qualifier(PRODUCT_CACHE_TASK_EXECUTOR)
    private val taskExecutor: TaskExecutor,
) {
    fun findList(command: ProductListCommand): Page<ProductSummary>? {
        return runCatching {
            productCacheRepository.findList(command)
        }.onFailure { e ->
            log.warn("Failed to read product list cache. command={}", command, e)
        }.getOrNull()
    }

    fun saveList(command: ProductListCommand, productSummaries: Page<ProductSummary>) {
        runCatching {
            taskExecutor.execute {
                runCatching {
                    productCacheRepository.saveList(command, productSummaries)
                }.onFailure { e ->
                    log.warn("Failed to save product list cache. command={}", command, e)
                }
            }
        }.onFailure { e ->
            log.warn("Failed to submit product list cache save task. command={}", command, e)
        }
    }

    fun refreshList(command: ProductListCommand) {
        runCatching {
            taskExecutor.execute {
                refreshListWithLock(command)
            }
        }.onFailure { e ->
            log.warn("Failed to submit product list cache refresh task. command={}", command, e)
        }
    }

    fun findDetail(productId: Long): ProductDetailInfo? {
        return runCatching {
            productCacheRepository.findDetail(productId)
        }.onFailure { e ->
            log.warn("Failed to read product detail cache. productId={}", productId, e)
        }.getOrNull()
    }

    fun saveDetail(productId: Long, productDetail: ProductDetailInfo) {
        runCatching {
            taskExecutor.execute {
                runCatching {
                    productCacheRepository.saveDetail(productId, productDetail)
                }.onFailure { e ->
                    log.warn("Failed to save product detail cache. productId={}", productId, e)
                }
            }
        }.onFailure { e ->
            log.warn("Failed to submit product detail cache save task. productId={}", productId, e)
        }
    }

    fun evictDetail(productId: Long) {
        runCatching {
            productCacheRepository.evictDetail(productId)
        }.onFailure { e ->
            log.warn("Failed to evict product detail cache. productId={}", productId, e)
        }
    }

    private fun refreshListWithLock(command: ProductListCommand) {
        var lockAcquired = false
        try {
            lockAcquired = productCacheRepository.acquireListRefreshLock(command)
            if (!lockAcquired) {
                return
            }

            productCacheRepository.saveList(command, productService.getProducts(command))
        } catch (e: Exception) {
            log.warn("Failed to refresh product list cache. command={}", command, e)
        } finally {
            releaseListRefreshLockIfAcquired(command, lockAcquired)
        }
    }

    private fun releaseListRefreshLockIfAcquired(command: ProductListCommand, lockAcquired: Boolean) {
        if (!lockAcquired) {
            return
        }

        runCatching {
            productCacheRepository.releaseListRefreshLock(command)
        }.onFailure { e ->
            log.warn("Failed to release product list cache refresh lock. command={}", command, e)
        }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(ProductCacheService::class.java)
    }
}
