package com.loopers.domain.like.application.service

data class LikeCountReconciliationResult(
    val productRows: Long,
    val likeRows: Long,
    val projectionRows: Long,
)
