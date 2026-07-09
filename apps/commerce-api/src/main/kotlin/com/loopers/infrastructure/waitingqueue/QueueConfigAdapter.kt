package com.loopers.infrastructure.waitingqueue

import com.loopers.domain.waitingqueue.model.QueueConfig
import com.loopers.domain.waitingqueue.model.QueueTopic
import com.loopers.domain.waitingqueue.port.QueueConfigPort
import org.springframework.stereotype.Component

/**
 * 설정 조회 어댑터. 캐시 우선 → 캐시 미스 시 DB → DB 에도 없으면 기본값.
 * 조회한 값은 캐시에 채워 다음 조회를 가속한다(캐시/DB 분기는 상위 계층에 은닉).
 */
@Component
class QueueConfigAdapter(
    private val cache: RedisQueueConfigCache,
    private val jpaRepository: QueueConfigJpaRepository,
) : QueueConfigPort {
    override fun get(topic: QueueTopic): QueueConfig {
        cache.read(topic)?.let { return it }

        val config = jpaRepository.findByTopic(topic.value)?.toDomain() ?: QueueConfig.default()
        cache.write(topic, config)
        return config
    }
}
