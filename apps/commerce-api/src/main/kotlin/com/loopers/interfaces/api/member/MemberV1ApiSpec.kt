package com.loopers.interfaces.api.member

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Member V1 API", description = "회원 API 입니다.")
interface MemberV1ApiSpec {
    @Operation(
        summary = "회원가입",
        description = "로그인 ID, 비밀번호, 이름, 생년월일, 이메일로 회원가입합니다.",
    )
    fun signUp(request: MemberV1Dto.SignUpRequest): ApiResponse<MemberV1Dto.SignUpResponse>
}
