package com.loopers.application.queue

data class QueuePosition(
    // 1-based, 미대기 시 null
    val position: Long?,
    val waiting: Boolean,
)
