package com.loopers.infrastructure.like.repository

import com.loopers.domain.like.model.Like
import com.loopers.domain.like.repository.LikeRepository
import com.loopers.infrastructure.like.mapper.LikeMapper
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component

@Component
class LikeRepositoryImpl(
    private val likeJpaRepository: LikeJpaRepository,
) : LikeRepository {
    override fun saveIfAbsent(like: Like): Boolean {
        if (likeJpaRepository.existsByMemberIdAndProductId(like.memberId, like.productId)) {
            return false
        }

        return try {
            likeJpaRepository.saveAndFlush(LikeMapper.toEntity(like))
            true
        } catch (e: DataIntegrityViolationException) {
            false
        }
    }

    override fun deleteIfExists(memberId: Long, productId: Long): Boolean {
        val entity = likeJpaRepository.findByMemberIdAndProductId(memberId, productId)
            ?: return false

        likeJpaRepository.delete(entity)
        return true
    }

    override fun findAllByMemberId(memberId: Long): List<Like> {
        return likeJpaRepository.findAllByMemberIdOrderByCreatedAtDescIdDesc(memberId)
            .map(LikeMapper::toDomain)
    }
}
