package com.loopers.domain.queue

import org.springframework.stereotype.Component

/**
 * 주문 대기열 도메인 서비스.
 * Redis Sorted Set 기반으로 진입 순서를 보장하고, 입장 토큰을 관리한다.
 */
@Component
class OrderQueueService(
    private val orderQueueRepository: OrderQueueRepository,
) {

    /**
     * 대기열에 진입한다.
     * 이미 진입한 경우 현재 순번을 반환한다.
     *
     * @param userId 진입 요청 사용자 ID
     * @return 대기열 진입 결과 (순번, 전체 대기 인원, 예상 대기 시간)
     */
    fun enter(userId: Long): QueuePositionInfo {
        val alreadyInQueue = orderQueueRepository.isInQueue(userId)
        if (!alreadyInQueue) {
            orderQueueRepository.addToQueue(userId)
        }
        return getPosition(userId)
    }

    /**
     * 현재 순번과 예상 대기 시간을 조회한다.
     * 토큰이 이미 발급된 경우 토큰 정보를 포함한다.
     *
     * @param userId 조회 대상 사용자 ID
     * @return 순번 정보 (토큰 발급 시 token 포함)
     */
    fun getPosition(userId: Long): QueuePositionInfo {
        val token = orderQueueRepository.getToken(userId)
        if (token != null) {
            return QueuePositionInfo(
                position = 0,
                totalWaiting = orderQueueRepository.getTotalWaiting(),
                estimatedWaitSeconds = 0,
                token = token,
            )
        }

        val rank = orderQueueRepository.getRank(userId)
            ?: return QueuePositionInfo(position = -1, totalWaiting = 0, estimatedWaitSeconds = 0, token = null)

        val totalWaiting = orderQueueRepository.getTotalWaiting()
        val estimatedWaitSeconds = calculateEstimatedWait(rank)

        return QueuePositionInfo(
            position = rank + 1,
            totalWaiting = totalWaiting,
            estimatedWaitSeconds = estimatedWaitSeconds,
            token = null,
        )
    }

    /**
     * 스케줄러가 호출: 대기열 앞에서 N명을 꺼내 토큰을 발급한다.
     *
     * @param batchSize 한 번에 처리할 인원 수
     * @return 토큰 발급된 사용자 수
     */
    fun processQueue(batchSize: Int): Int {
        val userIds = orderQueueRepository.popFromQueue(batchSize)
        userIds.forEach { userId ->
            orderQueueRepository.issueToken(userId)
        }
        return userIds.size
    }

    /**
     * 주문 완료 후 토큰을 삭제한다.
     */
    fun consumeToken(userId: Long) {
        orderQueueRepository.deleteToken(userId)
    }

    /**
     * 토큰이 유효한지 검증한다.
     *
     * @param userId 사용자 ID
     * @param token 검증 대상 토큰
     * @return 유효 여부
     */
    fun validateToken(userId: Long, token: String): Boolean {
        val storedToken = orderQueueRepository.getToken(userId)
        return storedToken != null && storedToken == token
    }

    /**
     * 예상 대기 시간 계산.
     * 스케줄러 배치 크기(18명/100ms = 180명/초)를 기준으로 산정.
     */
    private fun calculateEstimatedWait(rank: Long): Long {
        val throughputPerSecond = SCHEDULER_THROUGHPUT_PER_SECOND
        return if (throughputPerSecond > 0) (rank / throughputPerSecond) + 1 else 0
    }

    companion object {
        /** 스케줄러 초당 처리량 (18명 × 10회/초 = 180명/초) */
        const val SCHEDULER_THROUGHPUT_PER_SECOND = 180L

        /** 스케줄러 1회당 배치 크기 */
        const val SCHEDULER_BATCH_SIZE = 18
    }
}
