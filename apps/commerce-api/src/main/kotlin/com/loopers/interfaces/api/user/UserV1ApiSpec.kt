package com.loopers.interfaces.api.user

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "User V1 API", description = "유저 관련 API")
interface UserV1ApiSpec {
    @Operation(summary = "회원가입", description = "새로운 유저를 등록합니다.")
    fun register(request: UserV1Dto.RegisterRequest): ApiResponse<UserV1Dto.RegisterResponse>

    @Operation(summary = "내 정보 조회", description = "X-Loopers-LoginId, X-Loopers-LoginPw 헤더로 인증 후 유저 정보를 조회합니다.")
    fun getMe(): ApiResponse<UserV1Dto.MeResponse>

    @Operation(summary = "비밀번호 변경", description = "X-Loopers-LoginId, X-Loopers-LoginPw 헤더로 인증 후 새 비밀번호로 변경합니다.")
    fun changePassword(request: UserV1Dto.ChangePasswordRequest): ApiResponse<Unit>
}
