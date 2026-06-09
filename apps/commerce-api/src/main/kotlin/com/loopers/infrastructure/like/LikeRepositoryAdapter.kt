package com.loopers.infrastructure.like

import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult
import com.loopers.domain.like.Like
import com.loopers.domain.like.LikeRepositoryPort
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import org.springframework.data.domain.PageRequest as SpringPageRequest

@Component
class LikeRepositoryAdapter(
    private val likeJpaRepository: LikeJpaRepository,
    private val likeCountJpaRepository: LikeCountJpaRepository,
) : LikeRepositoryPort {
    override fun findByUserIdAndProductId(userId: Long, productId: Long): Like? =
        likeJpaRepository.findByUserIdAndProductId(userId, productId)?.toDomain()

    override fun save(like: Like): Like {
        val saved = likeJpaRepository.save(LikeEntity.from(like))
        val likeCount = likeCountJpaRepository.findByProductIdForUpdate(like.productId)
            ?: LikeCountEntity(productId = like.productId, count = 0)
        likeCount.increment()
        likeCountJpaRepository.save(likeCount)
        return saved.toDomain()
    }

    override fun delete(like: Like) {
        if (like.id == 0L) {
            throw CoreException(ErrorType.BAD_REQUEST, "ID가 없는 좋아요는 삭제할 수 없습니다.")
        }
        val entity = likeJpaRepository.findById(like.id)
            .orElseThrow { CoreException(ErrorType.NOT_FOUND, "좋아요를 찾을 수 없습니다.") }
        likeJpaRepository.delete(entity)
        likeCountJpaRepository.findByProductIdForUpdate(like.productId)?.also {
            it.decrement()
            likeCountJpaRepository.save(it)
        }
    }

    override fun deleteAllByProductId(productId: Long) {
        likeJpaRepository.deleteAllByProductId(productId)
        likeCountJpaRepository.deleteByProductId(productId)
    }

    override fun initializeForProduct(productId: Long) {
        if (likeCountJpaRepository.findByProductId(productId) == null) {
            likeCountJpaRepository.save(LikeCountEntity(productId = productId, count = 0))
        }
    }

    override fun findAllByUserId(userId: Long, pageRequest: PageRequest): PageResult<Like> {
        val springPageable = SpringPageRequest.of(
            pageRequest.page,
            pageRequest.size,
            Sort.by(Sort.Direction.DESC, "id"),
        )
        val page = likeJpaRepository.findAllByUserId(userId, springPageable)
        return PageResult.of(
            items = page.content.map { it.toDomain() },
            pageRequest = pageRequest,
            totalElements = page.totalElements,
        )
    }

    override fun findAllProductIdsByUserId(userId: Long): List<Long> =
        likeJpaRepository.findAllProductIdsByUserId(userId)
}
