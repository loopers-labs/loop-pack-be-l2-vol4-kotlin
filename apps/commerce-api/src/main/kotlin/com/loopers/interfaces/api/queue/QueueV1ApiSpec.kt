package com.loopers.interfaces.api.queue

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.RequestHeader

@Tag(name = "Queue V1 API", description = "대기열 API 입니다.")
interface QueueV1ApiSpec {
    @Operation(summary = "대기열 진입 (멱등)", description = "이미 대기 중이면 기존 순번, 토큰 보유자면 READY 를 반환.")
    fun enter(
        @Schema(name = "로그인 ID") @RequestHeader("X-Loopers-LoginId") loginId: String,
    ): ApiResponse<QueueV1Dto.QueueStatusResponse>

    @Operation(summary = "대기 상태 조회 (폴링용)", description = "WAITING(순번) / READY(토큰) / NOT_IN_QUEUE 중 하나.")
    fun status(
        @Schema(name = "로그인 ID") @RequestHeader("X-Loopers-LoginId") loginId: String,
    ): ApiResponse<QueueV1Dto.QueueStatusResponse>
}
