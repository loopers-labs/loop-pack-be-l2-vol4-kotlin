package com.loopers.infrastructure.like

import com.loopers.domain.like.Like
import com.loopers.domain.like.LikeRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component

@Component
class LikeRepositoryImpl(
    private val productLikeJpaRepository: ProductLikeJpaRepository,
) : LikeRepository {
    override fun saveIfAbsent(like: Like): Boolean {
        if (productLikeJpaRepository.existsByMemberIdAndProductId(like.memberId, like.productId)) {
            return false
        }

        return try {
            productLikeJpaRepository.saveAndFlush(ProductLikeMapper.toEntity(like))
            true
        } catch (e: DataIntegrityViolationException) {
            false
        }
    }

    override fun deleteIfExists(memberId: Long, productId: Long): Boolean {
        val entity = productLikeJpaRepository.findByMemberIdAndProductId(memberId, productId)
            ?: return false

        productLikeJpaRepository.delete(entity)
        return true
    }
}
