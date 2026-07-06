package com.loopers.interfaces.api.queue

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.auth.AuthUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Queue V1 API", description = "주문 대기열 API")
interface QueueV1ApiSpec {
    @Operation(
        summary = "대기열 진입",
        description = "인증 회원을 주문 대기열에 진입시키고 현재 순번을 반환합니다. 이미 진입한 경우 기존 순번을 유지합니다(멱등).",
    )
    fun enter(user: AuthUser): ApiResponse<QueueV1Dto.PositionResponse>

    @Operation(
        summary = "순번 조회",
        description = "인증 회원의 현재 순번과 전체 대기 인원을 조회합니다. 대기열에 없으면 순번은 null 입니다.",
    )
    fun position(user: AuthUser): ApiResponse<QueueV1Dto.PositionResponse>
}
