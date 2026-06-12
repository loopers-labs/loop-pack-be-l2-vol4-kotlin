package com.loopers.domain.user.presentation

import com.loopers.domain.user.application.UserFacade
import com.loopers.domain.user.application.info.UserInfo
import com.loopers.domain.user.presentation.auth.CurrentUserInfoRequestHeaders
import com.loopers.domain.user.presentation.auth.LoginUser
import com.loopers.domain.user.presentation.request.ChangePasswordRequest
import com.loopers.domain.user.presentation.request.SignUpRequest
import com.loopers.domain.user.presentation.response.MyUserResponse
import com.loopers.domain.user.presentation.response.SignUpResponse
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.swagger.v3.oas.annotations.Parameter
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/users")
class UserController(
    private val userFacade: UserFacade,
) : UserApiSpec {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    override fun signUp(
        @Valid @RequestBody request: SignUpRequest,
    ): ApiResponse<SignUpResponse> {
        return userFacade.signUp(request.toCommand())
            .let { SignUpResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/me")
    override fun getMe(
        @Parameter(hidden = true) @LoginUser user: UserInfo,
    ): ApiResponse<MyUserResponse> {
        return MyUserResponse.from(user)
            .let { ApiResponse.success(it) }
    }

    @PatchMapping("/me/password")
    override fun changePassword(
        @Parameter(hidden = true) @LoginUser user: UserInfo,
        @Parameter(hidden = true)
        @RequestHeader(name = CurrentUserInfoRequestHeaders.LOGIN_PW, required = false)
        currentRawPassword: String?,
        @Valid @RequestBody request: ChangePasswordRequest,
    ): ApiResponse<Any> {
        val rawPassword = currentRawPassword ?: throw CoreException(ErrorType.UNAUTHORIZED)
        userFacade.changePassword(request.toCommand(user.id, rawPassword))
        return ApiResponse.success()
    }
}
