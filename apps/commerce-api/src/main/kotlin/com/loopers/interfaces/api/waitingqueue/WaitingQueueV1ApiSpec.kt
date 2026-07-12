package com.loopers.interfaces.api.waitingqueue

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.waitingqueue.dto.WaitingQueueV1Dto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "WaitingQueue V1 API", description = "대기열 API 입니다.")
interface WaitingQueueV1ApiSpec {
    @Operation(
        summary = "대기열 진입",
        description = "로그인한 회원을 주문 대기열에 진입시키고 현재 순번을 반환합니다.",
    )
    fun enter(
        loginId: String,
        password: String,
    ): ApiResponse<WaitingQueueV1Dto.PositionResponse>

    @Operation(
        summary = "대기열 순번 조회",
        description = "로그인한 회원의 대기열 순번, 예상 대기 시간, 입장 토큰 발급 여부를 조회합니다.",
    )
    fun getPosition(
        loginId: String,
        password: String,
    ): ApiResponse<WaitingQueueV1Dto.PositionResponse>
}
