package com.loopers.domain.like

import com.loopers.domain.shared.CursorPage
import com.loopers.domain.shared.IdCursor

interface ProductLikeRepository {
    fun save(productLike: ProductLike): ProductLike

    fun existsByUserIdAndProductId(userId: Long, productId: Long): Boolean

    fun findByUserIdAndProductId(userId: Long, productId: Long): ProductLike?

    fun delete(productLike: ProductLike)

    fun findAllByUserId(userId: Long, cursor: IdCursor?, size: Int): CursorPage<ProductLike>
}
