package com.loopers.domain.waitingqueue.presentation

import com.loopers.domain.user.application.info.UserInfo
import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Waiting Queue API", description = "Loopers 주문 대기열 API 입니다.")
interface WaitingQueueApiSpec {
    @Operation(
        summary = "대기열 진입",
        description = "로그인 사용자를 주문 대기열에 등록하거나 기존 대기/입장 상태를 반환합니다.",
    )
    fun enter(user: UserInfo): ApiResponse<WaitingQueueResponse>

    @Operation(
        summary = "대기열 순번 조회",
        description = "로그인 사용자의 현재 대기 순번 또는 입장 토큰 상태를 조회합니다.",
    )
    fun position(user: UserInfo): ApiResponse<WaitingQueueResponse>
}
