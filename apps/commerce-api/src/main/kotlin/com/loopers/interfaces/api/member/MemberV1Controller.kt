package com.loopers.interfaces.api.member

import com.loopers.application.user.UserFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/members")
class MemberV1Controller(
    private val userFacade: UserFacade,
) : MemberV1ApiSpec {

    @PostMapping("/register")
    override fun register(
        @RequestBody request: MemberV1Dto.RegisterRequest,
    ): ApiResponse<MemberV1Dto.RegisterResponse> {
        return userFacade.register(
            loginId = request.loginId,
            password = request.password,
            name = request.name,
            birthDate = request.birthDate,
            email = request.email,
        )
            .let { MemberV1Dto.RegisterResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/me")
    override fun getMyInfo(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") password: String,
    ): ApiResponse<MemberV1Dto.MyInfoResponse> {
        return userFacade.getMyInfo(loginId, password)
            .let { MemberV1Dto.MyInfoResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @PatchMapping("/password")
    override fun changePassword(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") currentPassword: String,
        @RequestBody request: MemberV1Dto.ChangePasswordRequest,
    ): ApiResponse<Unit> {
        userFacade.changePassword(loginId, currentPassword, request.newPassword)
        return ApiResponse.success(Unit)
    }
}
