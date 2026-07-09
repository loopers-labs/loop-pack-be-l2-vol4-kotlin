package com.loopers.domain.waitingqueue.port

import com.loopers.domain.waitingqueue.model.QueueConfig
import com.loopers.domain.waitingqueue.model.QueueTopic

/**
 * 토픽별 설정 아웃바운드 포트. 캐시 우선 → DB → 기본값 로직은 어댑터에 은닉한다.
 */
interface QueueConfigPort {
    fun get(topic: QueueTopic): QueueConfig
}
