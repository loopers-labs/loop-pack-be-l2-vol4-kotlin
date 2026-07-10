package com.loopers.application.queue.usecase

import com.loopers.application.queue.QueuePosition
import com.loopers.domain.queue.OrderQueueRepository
import com.loopers.domain.user.UserService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class GetQueuePositionUsecase(
    private val userService: UserService,
    private val queueRepository: OrderQueueRepository,
    // 입장률 = token bucket refill rate — 예상 대기시간도 같은 값을 써야 드리프트가 없다.
    @Value("\${queue.refill-per-second:175}") private val refillPerSecond: Long,
) {
    private val log = LoggerFactory.getLogger(GetQueuePositionUsecase::class.java)

    fun execute(loginId: String, password: String): QueuePosition {
        val user = userService.getProfile(loginId = loginId, password = password)
        return try {
            val rank = queueRepository.rank(user.id)
            if (rank != null) {
                QueuePosition(
                    position = rank + 1,
                    waiting = true,
                    estimatedWaitSeconds = rank / refillPerSecond,
                    token = null,
                )
            } else {
                val token = queueRepository.findToken(user.id)
                if (token != null) {
                    QueuePosition(position = 0, waiting = false, estimatedWaitSeconds = 0, token = token)
                } else {
                    QueuePosition(position = null, waiting = false, estimatedWaitSeconds = null, token = null)
                }
            }
        } catch (e: Exception) {
            // fail-open degradation: Redis 장애 시 순번 조회 불가 → degraded 응답(주문은 게이트 bypass로 가능).
            log.warn("Redis unavailable — degraded queue position. userId={}", user.id, e)
            QueuePosition(position = null, waiting = false, estimatedWaitSeconds = null, token = null)
        }
    }
}
