package com.loopers.infrastructure.like

import com.loopers.domain.like.LikeEvent
import com.loopers.domain.like.LikeEventRepository
import org.springframework.stereotype.Repository

@Repository
class LikeEventRepositoryImpl(
    private val likeEventJpaRepository: LikeEventJpaRepository,
) : LikeEventRepository {
    override fun append(likeEvent: LikeEvent): LikeEvent =
        likeEventJpaRepository.save(likeEvent)
}
