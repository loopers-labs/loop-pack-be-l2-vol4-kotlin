package com.loopers.interfaces.api.queue

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.RequestHeader

@Tag(name = "Queue V1", description = "주문 대기열 API")
interface QueueV1ApiSpec {
    @Operation(summary = "대기열 진입 (ZADD NX — 이미 대기 중이면 최초 순번 유지)")
    fun enter(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") loginPw: String,
    ): ApiResponse<QueueV1Dto.QueueResponse>

    @Operation(summary = "내 순번 조회 (현재 순번 + 전체 대기 인원)")
    fun position(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") loginPw: String,
    ): ApiResponse<QueueV1Dto.QueueResponse>
}
