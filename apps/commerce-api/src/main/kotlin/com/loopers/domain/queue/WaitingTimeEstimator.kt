package com.loopers.domain.queue

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import kotlin.math.ceil

/**
 * 스케줄러가 [interval]마다 [batchSize] 명씩 입장시키는 정적 처리량을 기준으로,
 * 주어진 순번(rank)이 입장하기까지의 예상 대기 시간을 추정한다.
 *
 * 예상 대기 시간(초) = ceil(rank / batchSize) × interval(초)
 */
@Component
class WaitingTimeEstimator(
    @Value("\${queue.admission.batch-size:100}")
    private val batchSize: Long,
    @Value("\${queue.admission.interval-ms:1000}")
    private val intervalMs: Long,
) {
    fun estimateSeconds(rank: Long): Long {
        if (batchSize <= 0) {
            return 0L
        }
        val intervals = ceil(rank.toDouble() / batchSize).toLong()
        return intervals * intervalMs / MILLIS_PER_SECOND
    }

    companion object {
        private const val MILLIS_PER_SECOND = 1000L
    }
}
