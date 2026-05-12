package com.loopers.interfaces.api.member

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Member V1 API", description = "회원 API 입니다.")
interface MemberV1ApiSpec {
    @Operation(
        summary = "회원가입",
        description = "회원가입을 합니다.",
    )
    fun register(request: MemberV1Dto.RegisterRequest): ApiResponse<MemberV1Dto.RegisterResponse>

    @Operation(
        summary = "내 정보 조회",
        description = "내 정보를 조회합니다.",
    )
    fun getMyInfo(loginId: String, password: String): ApiResponse<MemberV1Dto.MyInfoResponse>
}