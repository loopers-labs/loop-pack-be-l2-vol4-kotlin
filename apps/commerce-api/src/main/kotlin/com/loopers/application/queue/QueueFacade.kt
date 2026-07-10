package com.loopers.application.queue

import com.loopers.domain.queue.EntryTokenService
import com.loopers.domain.queue.WaitingQueueService
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

/**
 * 대기열 유즈케이스 조립.
 *
 * 로그인 헤더로 인증해 userId 를 얻은 뒤 대기열 도메인 서비스에 위임한다.
 * Redis 만 사용하므로 별도 트랜잭션 경계를 두지 않는다(인증의 조회 트랜잭션은 UserService 내부에서 관리).
 */
@Component
class QueueFacade(
    private val userService: UserService,
    private val waitingQueueService: WaitingQueueService,
    private val entryTokenService: EntryTokenService,
) {
    fun enter(loginId: String, rawPassword: String): QueueInfo {
        val user = userService.authenticate(loginId, rawPassword)
        return waiting(waitingQueueService.enter(user.id))
    }

    fun position(loginId: String, rawPassword: String): QueueInfo {
        val user = userService.authenticate(loginId, rawPassword)

        val rank = waitingQueueService.position(user.id)
        if (rank != null) {
            return waiting(rank)
        }

        // 큐에 없다면 이미 입장(토큰 발급)했는지 확인한다. 토큰이 있으면 READY, 없으면 미진입/만료.
        val token = entryTokenService.find(user.id)
            ?: throw CoreException(ErrorType.NOT_FOUND, "대기열에 진입한 이력이 없거나 입장 시간이 만료되었습니다.")
        return QueueInfo.ready(token)
    }

    private fun waiting(rank: Long): QueueInfo =
        QueueInfo.waiting(rank, waitingQueueService.size(), waitingQueueService.estimatedWaitSeconds(rank))
}
