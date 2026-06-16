package com.loopers.like.infrastructure

import com.loopers.like.domain.LikeEvent
import com.loopers.like.domain.LikeEventRepository
import org.springframework.stereotype.Repository

@Repository
class LikeEventRepositoryImpl(
    private val likeEventJpaRepository: LikeEventJpaRepository,
) : LikeEventRepository {
    override fun append(likeEvent: LikeEvent): LikeEvent =
        likeEventJpaRepository.save(likeEvent)
}
