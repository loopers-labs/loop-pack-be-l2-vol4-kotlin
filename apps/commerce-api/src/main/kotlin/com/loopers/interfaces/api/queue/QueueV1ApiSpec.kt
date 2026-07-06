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
        summary = "순번 조회 (Polling)",
        description = "현재 순번·전체 대기 인원·예상 대기 시간(초)을 조회합니다. " +
            "입장 처리되면 응답에 entryToken 이 채워지고 순번은 null 이 됩니다 — 그 토큰으로 주문 API 를 호출합니다. " +
            "클라이언트가 1~3초 주기로 polling 하며, 대기 인원이 많으면 조회 부하(인원 × 주기)가 크므로 구간별 주기 조절은 Nice-To-Have 입니다.",
    )
    fun position(user: AuthUser): ApiResponse<QueueV1Dto.PositionResponse>
}
