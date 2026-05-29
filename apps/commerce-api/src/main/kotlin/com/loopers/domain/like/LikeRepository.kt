package com.loopers.domain.like

interface LikeRepository {
    fun saveIfAbsent(like: Like): Boolean
}
