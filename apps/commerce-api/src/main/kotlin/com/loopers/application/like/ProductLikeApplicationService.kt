package com.loopers.application.like

import com.loopers.application.catalog.port.LikeProductQueryPort
import com.loopers.domain.like.LikeAction
import com.loopers.domain.like.ProductLikeCursorRepository
import com.loopers.domain.like.ProductLikeHistory
import com.loopers.domain.like.ProductLikeHistoryRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProductLikeApplicationService(
    private val historyRepository: ProductLikeHistoryRepository,
    private val cursorRepository: ProductLikeCursorRepository,
) : LikeProductQueryPort {
    @Transactional
    fun register(userId: Long, productId: Long): ProductLikeResult {
        val cursor = cursorRepository.findOrCreateForUpdate(userId, productId)
        val latest = findLatest(cursor.lastHistoryId, userId, productId)

        if (latest?.action == LikeAction.REGISTER) {
            return ProductLikeResult(productId = productId, liked = true, changed = false)
        }

        val history = historyRepository.save(
            ProductLikeHistory(userId = userId, productId = productId, action = LikeAction.REGISTER),
        )
        cursor.moveTo(history)
        cursorRepository.save(cursor)

        return ProductLikeResult(productId = productId, liked = true, changed = true)
    }

    @Transactional
    fun cancel(userId: Long, productId: Long): ProductLikeResult {
        val cursor = cursorRepository.findOrCreateForUpdate(userId, productId)
        val latest = findLatest(cursor.lastHistoryId, userId, productId)

        if (latest?.action != LikeAction.REGISTER) {
            return ProductLikeResult(productId = productId, liked = false, changed = false)
        }

        val history = historyRepository.save(
            ProductLikeHistory(userId = userId, productId = productId, action = LikeAction.CANCEL),
        )
        cursor.moveTo(history)
        cursorRepository.save(cursor)

        return ProductLikeResult(productId = productId, liked = false, changed = true)
    }

    @Transactional(readOnly = true)
    override fun getLikedProductIds(userId: Long, productIds: Collection<Long>): Set<Long> {
        if (productIds.isEmpty()) return emptySet()
        return historyRepository.findLikedProductIds(userId, productIds)
    }

    @Transactional(readOnly = true)
    override fun isLiked(userId: Long, productId: Long): Boolean =
        historyRepository.findLatest(userId, productId)?.action == LikeAction.REGISTER

    private fun findLatest(lastHistoryId: Long?, userId: Long, productId: Long): ProductLikeHistory? =
        lastHistoryId?.let { historyRepository.findById(it) }
            ?: historyRepository.findLatest(userId, productId)
}
