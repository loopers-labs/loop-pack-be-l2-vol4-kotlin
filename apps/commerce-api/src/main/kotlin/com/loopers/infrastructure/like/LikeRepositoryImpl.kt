package com.loopers.infrastructure.like

import com.loopers.domain.like.Like
import com.loopers.domain.like.LikeRepository
import com.loopers.domain.product.dto.ProductSummary
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
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

    override fun findLikedProductSummaries(memberId: Long, page: Int, size: Int): Page<ProductSummary> {
        return productLikeJpaRepository.findLikedProductSummaries(
            memberId = memberId,
            pageable = PageRequest.of(page, size),
        )
    }
}
