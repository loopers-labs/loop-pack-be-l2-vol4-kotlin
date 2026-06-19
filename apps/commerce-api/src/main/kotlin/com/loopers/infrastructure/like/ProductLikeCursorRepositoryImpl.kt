package com.loopers.infrastructure.like

import com.loopers.domain.like.ProductLikeCursor
import com.loopers.domain.like.ProductLikeCursorRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class ProductLikeCursorRepositoryImpl(
    private val productLikeCursorJpaRepository: ProductLikeCursorJpaRepository,
) : ProductLikeCursorRepository {
    override fun findOrCreateForUpdate(userId: Long, productId: Long): ProductLikeCursor {
        productLikeCursorJpaRepository.insertIgnore(userId, productId)
        return productLikeCursorJpaRepository.findByUserIdAndProductIdForUpdate(userId, productId)
            ?: throw CoreException(ErrorType.CONFLICT, "좋아요 cursor 를 획득하지 못했습니다.")
    }

    override fun save(cursor: ProductLikeCursor): ProductLikeCursor =
        productLikeCursorJpaRepository.save(cursor)
}
