package com.loopers.domain.like

interface LikeEventRepository {
    fun append(likeEvent: LikeEvent): LikeEvent
}
