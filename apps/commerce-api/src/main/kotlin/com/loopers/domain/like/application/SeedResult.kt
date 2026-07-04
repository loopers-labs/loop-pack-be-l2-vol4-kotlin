package com.loopers.domain.like.application

data class SeedResult(
    val likesInserted: Int,
    val countsInserted: Int,
    val elapsedMillis: Long,
    val skipped: Boolean,
)
