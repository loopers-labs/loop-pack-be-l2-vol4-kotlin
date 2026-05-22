package com.loopers.interfaces.api.user

import com.loopers.domain.user.User
import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "User V1 API", description = "Loopers 사용자 API 입니다.")
interface UserV1ApiSpec {
    @Operation(summary = "회원가입", description = "신규 사용자를 생성합니다.")
    fun signUp(request: UserV1Dto.SignUpRequest): ApiResponse<UserV1Dto.MyInfoResponse>

    @Operation(summary = "내 정보 조회", description = "헤더 인증 후 본인 정보를 반환합니다.")
    fun getMyInfo(user: User): ApiResponse<UserV1Dto.MyInfoResponse>

    @Operation(summary = "비밀번호 변경", description = "기존 비밀번호 확인 후 비밀번호를 변경합니다.")
    fun changePassword(user: User, request: UserV1Dto.ChangePasswordRequest): ApiResponse<Unit>
}
