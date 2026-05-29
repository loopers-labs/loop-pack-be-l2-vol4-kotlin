package com.loopers.interfaces.api.user

import com.loopers.application.user.UserApplicationService
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.auth.LoginAuth
import com.loopers.support.auth.LoginUser
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserV1Controller(
    private val userApplicationService: UserApplicationService,
) : UserV1ApiSpec {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    override fun signUp(
        @Valid @RequestBody request: UserV1Dto.SignUpRequest,
    ): ApiResponse<UserV1Dto.SignUpResponse> {
        val info = userApplicationService.signUp(
            loginId = request.loginId,
            rawPassword = request.password,
            name = request.name,
            birthDate = request.birthDate,
            email = request.email,
        )
        return ApiResponse.success(UserV1Dto.SignUpResponse.from(info))
    }

    @GetMapping("/me")
    override fun getUserInfo(@LoginAuth loginUser: LoginUser): ApiResponse<UserV1Dto.GetUserInfoResponse> {
        val info = userApplicationService.getUserInfo(
            loginId = loginUser.loginId,
            rawPassword = loginUser.rawPassword,
        )
        return ApiResponse.success(UserV1Dto.GetUserInfoResponse.from(info))
    }

    @PatchMapping("/password")
    override fun changePassword(
        @LoginAuth loginUser: LoginUser,
        @Valid @RequestBody request: UserV1Dto.ChangePasswordRequest,
    ): ApiResponse<Unit> {
        userApplicationService.changePassword(loginUser.loginId, loginUser.rawPassword, request.newPassword)
        return ApiResponse.success(null)
    }
}
