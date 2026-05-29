package com.loopers.infrastructure.like

import com.loopers.domain.like.ProductLike
import com.loopers.domain.like.ProductLikeRepository
import com.loopers.domain.shared.CursorPage
import com.loopers.domain.shared.IdCursor
import org.springframework.data.domain.Limit
import org.springframework.data.domain.ScrollPosition
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component

@Component
class ProductLikeRepositoryImpl(
    private val productLikeJpaRepository: ProductLikeJpaRepository,
) : ProductLikeRepository {
    override fun save(productLike: ProductLike): ProductLike =
        productLikeJpaRepository.save(productLike)

    override fun existsByUserIdAndProductId(userId: Long, productId: Long): Boolean =
        productLikeJpaRepository.existsByUserIdAndProductId(userId, productId)

    override fun findByUserIdAndProductId(userId: Long, productId: Long): ProductLike? =
        productLikeJpaRepository.findByUserIdAndProductId(userId, productId)

    override fun delete(productLike: ProductLike) {
        productLikeJpaRepository.delete(productLike)
    }

    override fun findAllByUserId(userId: Long, cursor: IdCursor?, size: Int): CursorPage<ProductLike> {
        val scrollPosition =
            if (cursor == null) {
                ScrollPosition.keyset()
            } else {
                ScrollPosition.of(mapOf<String, Any>("id" to cursor.id), ScrollPosition.Direction.FORWARD)
            }
        val window = productLikeJpaRepository.findByUserId(
            userId,
            scrollPosition,
            Limit.of(size),
            Sort.by(Sort.Direction.DESC, "id"),
        )
        val nextCursor =
            if (window.hasNext() && window.content.isNotEmpty()) {
                IdCursor(window.content.last().id)
            } else {
                null
            }
        return CursorPage(window.content, window.hasNext(), nextCursor)
    }
}
