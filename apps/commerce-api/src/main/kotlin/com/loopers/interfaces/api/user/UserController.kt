package com.loopers.interfaces.api.user

import com.loopers.application.user.UserService
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/user")
class UserController(
    private val userService: UserService,
) {
    @PostMapping("/signup")
    fun signup(
        @RequestBody request: UserV1Dto.SignupRequest,
    ): ApiResponse<Any> {
        userService.signup(request.toDomain())
        return ApiResponse.success()
    }
}
