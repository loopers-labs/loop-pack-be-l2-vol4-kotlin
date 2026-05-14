package com.loopers.interfaces.api.user

import com.loopers.application.user.UserFacade
import com.loopers.application.user.UserInfo
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.interceptor.LoginInterceptor
import jakarta.servlet.http.HttpServletRequest
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
    private val userFacade: UserFacade,
    private val request: HttpServletRequest,
) : UserV1ApiSpec {
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    override fun register(
        @RequestBody request: UserV1Dto.RegisterRequest,
    ): ApiResponse<UserV1Dto.RegisterResponse> {
        return userFacade.register(
            loginId = request.loginId,
            rawPassword = request.password,
            name = request.name,
            birthDate = request.birthDate,
            email = request.email,
        ).let { UserV1Dto.RegisterResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/me")
    override fun getMe(): ApiResponse<UserV1Dto.MeResponse> {
        val loginUser = request.getAttribute(LoginInterceptor.LOGIN_USER_ATTRIBUTE) as UserInfo
        return UserV1Dto.MeResponse.from(loginUser)
            .let { ApiResponse.success(it) }
    }

    @PatchMapping("/me/password")
    override fun changePassword(
        @RequestBody request: UserV1Dto.ChangePasswordRequest,
    ): ApiResponse<Unit> {
        val loginUser = this.request.getAttribute(LoginInterceptor.LOGIN_USER_ATTRIBUTE) as UserInfo
        userFacade.changePassword(
            userId = loginUser.id,
            currentRawPassword = request.currentPassword,
            newRawPassword = request.newPassword,
        )
        return ApiResponse.success(null)
    }
}
