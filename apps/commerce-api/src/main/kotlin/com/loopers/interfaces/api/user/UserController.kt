package com.loopers.interfaces.api.user

import com.loopers.application.user.UserFacade
import com.loopers.application.user.UserService
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/user")
class UserController(
    private val userService: UserService,
    private val userFacade: UserFacade,
) {
    @PostMapping("/signup")
    fun signup(
        @RequestBody request: UserV1Dto.SignupRequest,
    ): ApiResponse<Any> {
        userService.signup(request.toDomain())
        return ApiResponse.success()
    }

    @GetMapping("/info")
    fun getMyInfo(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") loginPw: String,
    ): ApiResponse<UserV1Dto.UserInfoResponse> {
        val user = userService.login(loginId, loginPw)
        return ApiResponse.success(UserV1Dto.UserInfoResponse.from(user))
    }

    @PutMapping("/password")
    fun changePassword(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") loginPw: String,
        @RequestBody request: UserV1Dto.ChangePasswordRequest,
    ): ApiResponse<Any> {
        userFacade.changePw(request.toCommand(loginId, loginPw))
        return ApiResponse.success()
    }
}
