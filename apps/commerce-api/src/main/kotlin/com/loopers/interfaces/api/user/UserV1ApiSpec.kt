package com.loopers.interfaces.api.user

import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.auth.LoginAuth
import com.loopers.support.auth.LoginUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus

@Tag(name = "User", description = "회원 API")
interface UserV1ApiSpec {
    @Operation(summary = "회원가입")
    @ResponseStatus(HttpStatus.CREATED)
    fun signUp(
        @Valid @RequestBody request: UserV1Dto.SignUpRequest,
    ): ApiResponse<UserV1Dto.SignUpResponse>

    @Operation(summary = "내 정보 조회")
    fun getUserInfo(@LoginAuth loginUser: LoginUser): ApiResponse<UserV1Dto.GetUserInfoResponse>

    @Operation(summary = "비밀번호 수정")
    fun changePassword(
        @LoginAuth loginUser: LoginUser,
        @Valid @RequestBody request: UserV1Dto.ChangePasswordRequest,
    ): ApiResponse<Unit>
}
