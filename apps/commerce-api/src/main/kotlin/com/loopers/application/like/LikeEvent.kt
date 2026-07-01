package com.loopers.application.like

data class LikeEvent(
    val userId: Long,
    val productId: Long,
    val action: LikeAction,
) {
    enum class LikeAction { LIKED, UNLIKED }
}
