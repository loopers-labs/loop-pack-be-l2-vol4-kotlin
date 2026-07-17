package com.loopers.infrastructure.queue

import com.loopers.domain.queue.OrderQueueService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 주문 대기열 스케줄러.
 * 100ms 간격으로 대기열에서 N명씩 꺼내 입장 토큰을 발급한다.
 *
 * 배치 크기 산정 근거:
 * - DB 커넥션 풀: 50
 * - 주문 1건 평균 처리 시간: 200ms
 * - 이론적 최대 TPS: 50 / 0.2 = 250 TPS
 * - 안전 마진 70%: 175 TPS
 * - 100ms 간격 → 1초에 10회 실행 → 배치 크기 = 175 / 10 ≈ 18명
 *
 * Thundering Herd 완화: 1초에 175명을 한 번에 발급하지 않고 100ms 단위로 분산.
 */
@Component
class OrderQueueScheduler(
    private val orderQueueService: OrderQueueService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 100ms마다 대기열에서 18명씩 꺼내 토큰을 발급한다.
     */
    @Scheduled(fixedDelay = 100)
    fun processQueue() {
        val processed = orderQueueService.processQueue(OrderQueueService.SCHEDULER_BATCH_SIZE)
        if (processed > 0) {
            log.info("[대기열] {} 명 토큰 발급 완료", processed)
        }
    }
}
