package com.loopers.projection.like.application

data class LikeCountProjectionResult(
    val status: LikeCountProjectionStatus,
) {
    companion object {
        fun applied(): LikeCountProjectionResult = LikeCountProjectionResult(LikeCountProjectionStatus.APPLIED)

        fun duplicate(): LikeCountProjectionResult = LikeCountProjectionResult(LikeCountProjectionStatus.DUPLICATE)
    }
}

enum class LikeCountProjectionStatus {
    APPLIED,
    DUPLICATE,
}
