package com.loopers.domain.queue

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * 대기열 도메인 서비스.
 *
 * 인증(로그인 → userId)은 상위 레이어의 책임이고, 여기서는 순수하게 userId 만 다룬다.
 * 폴링되는 순번 조회가 매번 인증(BCrypt)을 거치지 않도록 경계를 분리한 것.
 */
@Component
class WaitingQueueService(
    private val waitingQueueRepository: WaitingQueueRepository,
    @Value("\${queue.throughput-per-second:100}") private val throughputPerSecond: Long,
) {
    /** 대기열에 진입시키고 0-based 순번을 반환한다. 이미 대기 중이면 최초 순번을 유지한다. */
    fun enter(userId: Long): Long {
        waitingQueueRepository.enter(userId, Instant.now())
        return waitingQueueRepository.position(userId)
            ?: throw CoreException(ErrorType.INTERNAL_ERROR, "대기열 진입 직후 순번을 조회하지 못했습니다.")
    }

    /** 0-based 순번. 대기열에 없으면 null. */
    fun position(userId: Long): Long? = waitingQueueRepository.position(userId)

    /** 전체 대기 인원. */
    fun size(): Long = waitingQueueRepository.size()

    /** 앞에서 count 명을 꺼내(제거) 입장시킬 userId 목록을 반환한다. */
    fun poll(count: Long): List<Long> = waitingQueueRepository.poll(count)

    /** 예상 대기 시간(초) = 0-based 순번 / 초당 처리량. 추정값이므로 "약 N초"로 표기한다. */
    fun estimatedWaitSeconds(rank: Long): Long =
        if (throughputPerSecond <= 0) 0L else rank / throughputPerSecond
}
