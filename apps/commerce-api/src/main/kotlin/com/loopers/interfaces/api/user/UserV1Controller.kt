package com.loopers.interfaces.api.user

import com.loopers.application.user.UserFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserV1Controller(
    private val userFacade: UserFacade,
) : UserV1ApiSpec {
    @PostMapping
    override fun signUp(
        @RequestBody request: UserV1Dto.SignUpRequest,
    ): ApiResponse<UserV1Dto.SignUpResponse> {
        return userFacade.signUp(request.toCommand())
            .let { UserV1Dto.SignUpResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/me")
    override fun getMe(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") password: String,
    ): ApiResponse<UserV1Dto.GetMeResponse> {
        return userFacade.getMe(loginId, password)
            .let { UserV1Dto.GetMeResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @PutMapping("/password")
    override fun updatePassword(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") password: String,
        @RequestBody request: UserV1Dto.UpdatePasswordRequest,
    ): ApiResponse<Any> {
        userFacade.updatePassword(
            loginId = loginId,
            rawPassword = password,
            newRawPassword = request.newPassword,
        )

        return ApiResponse.success()
    }
}
