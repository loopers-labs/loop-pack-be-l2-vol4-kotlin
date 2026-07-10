package com.loopers.interfaces.api.waitingqueue

import com.loopers.domain.user.User
import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Waiting Queue V1 API", description = "Loopers 주문 대기열 API 입니다.")
interface WaitingQueueV1ApiSpec {
    @Operation(summary = "주문 대기열 상태 조회", description = "주문 대기열 토큰의 남은 시간과 대기 인원을 조회합니다.")
    fun poll(user: User, token: String): ApiResponse<WaitingQueueV1Dto.PollResponse>

    @Operation(summary = "주문 대기열 컨슈머 헬스 조회", description = "대기열 입장 배치의 heartbeat 상태를 조회합니다.")
    fun health(): ApiResponse<WaitingQueueV1Dto.HealthResponse>
}
