package com.loopers.interfaces.api.waitingqueue

import com.loopers.application.waitingqueue.EnterCommand
import com.loopers.application.waitingqueue.WaitTokenResult

/**
 * 대기열 대고객 인바운드 포트. 컨트롤러/인터셉터가 의존하는 진입점.
 */
interface QueueApplicationServicePort {
    /** 대기열 (재)진입. 기존 위치를 제거하고 맨 뒤로 등록한 뒤 새 대기열 토큰을 발급한다. */
    fun enter(command: EnterCommand): WaitTokenResult
}
