package com.loopers.domain.like.event

object ProductLikeEvent {
    data class Like(
        val memberId: Long,
        val productId: Long,
        val brandId: Long,
    )

    data class Unlike(
        val memberId: Long,
        val productId: Long,
        val brandId: Long,
    )
}
