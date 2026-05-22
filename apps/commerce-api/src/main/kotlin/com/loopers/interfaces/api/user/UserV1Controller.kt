package com.loopers.interfaces.api.user

import com.loopers.domain.user.RawPassword
import com.loopers.domain.user.User
import com.loopers.domain.user.UserService
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.auth.CurrentUser
import com.loopers.support.auth.LoginRequired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserV1Controller(
    private val userService: UserService,
) : UserV1ApiSpec {
    @PostMapping
    override fun signUp(
        @RequestBody request: UserV1Dto.SignUpRequest,
    ): ApiResponse<UserV1Dto.MyInfoResponse> {
        val user = userService.register(request.toCommand())
        return ApiResponse.success(UserV1Dto.MyInfoResponse.from(user))
    }

    @LoginRequired
    @GetMapping("/me")
    override fun getMyInfo(
        @CurrentUser user: User,
    ): ApiResponse<UserV1Dto.MyInfoResponse> = ApiResponse.success(UserV1Dto.MyInfoResponse.from(user))

    @LoginRequired
    @PatchMapping("/me/password")
    override fun changePassword(
        @CurrentUser user: User,
        @RequestBody request: UserV1Dto.ChangePasswordRequest,
    ): ApiResponse<Unit> {
        userService.changePassword(user.id, RawPassword(request.oldPassword), RawPassword(request.newPassword))
        return ApiResponse.success(Unit)
    }
}
