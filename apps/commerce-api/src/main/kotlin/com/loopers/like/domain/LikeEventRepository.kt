package com.loopers.like.domain

interface LikeEventRepository {
    fun append(likeEvent: LikeEvent): LikeEvent
}
