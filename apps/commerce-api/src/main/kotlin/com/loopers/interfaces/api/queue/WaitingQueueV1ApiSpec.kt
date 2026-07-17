package com.loopers.interfaces.api.queue

import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.auth.LoginAuth
import com.loopers.support.auth.LoginUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@Tag(name = "Waiting Queue", description = "대기열 API")
interface WaitingQueueV1ApiSpec {
    @Operation(
        summary = "대기열 진입",
        description = "주문 대기열에 진입해 status=WAITING 과 순번/전체 인원을 반환한다. " +
            "아직 입장 전인 유저가 다시 진입하면 맨 뒤로 재배치되고, " +
            "이미 입장 처리(토큰 발급)된 유저가 다시 진입하면 재진입 없이 status=READY 와 token 을 그대로 반환한다.",
    )
    @ResponseStatus(HttpStatus.OK)
    fun enter(
        @LoginAuth loginUser: LoginUser,
    ): ApiResponse<WaitingQueueV1Dto.PositionResponse>

    @Operation(
        summary = "대기열 순번 조회",
        description = "대기열에서 자신의 상태를 조회한다. 아직 대기 중이면 status=WAITING 과 순번/전체 인원/예상 대기 시간(초)을, " +
            "입장 처리되어 토큰이 발급됐으면 status=READY 와 token 을 반환한다. " +
            "대기열에도 없고 토큰도 없으면 404를 반환한다.",
    )
    fun getPosition(
        @LoginAuth loginUser: LoginUser,
    ): ApiResponse<WaitingQueueV1Dto.PositionResponse>
}
