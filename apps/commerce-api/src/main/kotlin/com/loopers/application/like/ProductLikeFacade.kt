package com.loopers.application.like

import com.loopers.application.catalog.port.CatalogProductStatsCommandPort
import org.springframework.dao.CannotAcquireLockException
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

@Component
class ProductLikeFacade(
    private val likeService: ProductLikeApplicationService,
    private val catalogProductStatsCommandPort: CatalogProductStatsCommandPort,
    private val transactionTemplate: TransactionTemplate,
) {
    fun register(userId: Long, productId: Long): ProductLikeInfo {
        return executeWithLockRetry {
            val result = likeService.register(userId, productId)
            if (result.changed) {
                catalogProductStatsCommandPort.increaseLikeCount(productId)
            }
            ProductLikeInfo(productId = result.productId, liked = result.liked)
        }
    }

    fun cancel(userId: Long, productId: Long): ProductLikeInfo {
        return executeWithLockRetry {
            val result = likeService.cancel(userId, productId)
            if (result.changed) {
                catalogProductStatsCommandPort.decreaseLikeCount(productId)
            }
            ProductLikeInfo(productId = result.productId, liked = result.liked)
        }
    }

    fun isLiked(userId: Long, productId: Long): ProductLikeInfo =
        ProductLikeInfo(productId = productId, liked = likeService.isLiked(userId, productId))

    private fun executeWithLockRetry(block: () -> ProductLikeInfo): ProductLikeInfo {
        repeat(MAX_LOCK_RETRY_COUNT - 1) { attempt ->
            try {
                return transactionTemplate.execute { block() }
                    ?: throw IllegalStateException("좋아요 transaction 결과가 없습니다.")
            } catch (e: CannotAcquireLockException) {
                // Retry in a new transaction after MySQL deadlock or lock acquisition failure.
                Thread.sleep((attempt + 1L) * LOCK_RETRY_BACKOFF_MILLIS)
            }
        }

        return transactionTemplate.execute { block() }
            ?: throw IllegalStateException("좋아요 transaction 결과가 없습니다.")
    }

    companion object {
        private const val MAX_LOCK_RETRY_COUNT = 10
        private const val LOCK_RETRY_BACKOFF_MILLIS = 10L
    }
}
