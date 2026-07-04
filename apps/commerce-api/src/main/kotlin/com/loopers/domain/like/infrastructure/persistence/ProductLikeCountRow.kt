package com.loopers.domain.like.infrastructure.persistence

interface ProductLikeCountRow {
    fun getProductId(): Long
    fun getLikeCount(): Long
}
