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
        description = "주문 대기열에 진입하고 자신의 순번과 전체 대기 인원을 반환한다. 이미 진입한 유저가 다시 진입하면 맨 뒤로 재배치된다.",
    )
    @ResponseStatus(HttpStatus.OK)
    fun enter(
        @LoginAuth loginUser: LoginUser,
    ): ApiResponse<WaitingQueueV1Dto.EnterResponse>

    @Operation(
        summary = "대기열 순번 조회",
        description = "대기열에서 자신의 현재 순번과 전체 대기 인원을 조회한다. 대기열에 없는 유저가 조회하면 404를 반환한다.",
    )
    fun getPosition(
        @LoginAuth loginUser: LoginUser,
    ): ApiResponse<WaitingQueueV1Dto.PositionResponse>
}
