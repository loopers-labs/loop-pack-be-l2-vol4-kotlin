package com.loopers.interfaces.api.user

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "User V1 API", description = "유저 API 입니다.")
interface UserV1ApiSpec {
    @Operation(
        summary = "회원가입",
        description = "로그인 ID, 비밀번호, 이름, 생년월일, 이메일로 회원가입합니다.",
    )
    fun signUp(request: UserV1Dto.SignUpRequest): ApiResponse<UserV1Dto.SignUpResponse>

    @Operation(
        summary = "내 정보 조회",
        description = "로그인 ID와 비밀번호 헤더로 내 정보를 조회합니다.",
    )
    fun getMe(
        loginId: String,
        password: String,
    ): ApiResponse<UserV1Dto.GetMeResponse>

    @Operation(
        summary = "비밀번호 수정",
        description = "로그인 ID와 기존 비밀번호 헤더로 인증한 뒤 새 비밀번호로 수정합니다.",
    )
    fun updatePassword(
        loginId: String,
        password: String,
        request: UserV1Dto.UpdatePasswordRequest,
    ): ApiResponse<Any>
}
