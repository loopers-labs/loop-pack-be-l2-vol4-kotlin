package com.loopers.application.queue

data class QueuePosition(
    // 1-based, 미대기 시 null (토큰 발급 시 0)
    val position: Long?,
    val waiting: Boolean,
    // 대기 중일 때만: rank / throughputPerSecond(초 단위, 정수)
    val estimatedWaitSeconds: Long?,
    // 발급된 입장 토큰
    val token: String?,
)
