package com.loopers.domain.user.presentation

import com.loopers.domain.user.application.UserInfo
import com.loopers.domain.user.presentation.request.ChangePasswordRequest
import com.loopers.domain.user.presentation.request.SignUpRequest
import com.loopers.domain.user.presentation.response.MyUserResponse
import com.loopers.domain.user.presentation.response.SignUpResponse
import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "User API", description = "Loopers 사용자 API 입니다.")
interface UserApiSpec {
    @Operation(
        summary = "회원 가입",
        description = "신규 회원을 등록합니다.",
    )
    fun signUp(request: SignUpRequest): ApiResponse<SignUpResponse>

    @Operation(
        summary = "내 정보 조회",
        description = "인증 헤더 기반으로 로그인한 사용자의 정보를 조회합니다.",
    )
    fun getMe(user: UserInfo): ApiResponse<MyUserResponse>

    @Operation(
        summary = "비밀번호 변경",
        description = "인증 헤더 기반으로 로그인한 사용자의 비밀번호를 변경합니다.",
    )
    fun changePassword(
        user: UserInfo,
        currentRawPassword: String?,
        request: ChangePasswordRequest,
    ): ApiResponse<Any>
}
