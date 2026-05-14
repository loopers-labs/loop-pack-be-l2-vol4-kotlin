package com.loopers.interfaces.api.user

import com.loopers.application.user.UserFacade
import com.loopers.interfaces.api.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserV1Controller(
    private val userFacade: UserFacade,
) : UserV1ApiSpec {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    override fun signUp(
        @Valid @RequestBody request: UserV1Dto.SignUpRequest,
    ): ApiResponse<UserV1Dto.SignUpResponse> {
        val info = userFacade.signUp(
            loginId = request.loginId,
            rawPassword = request.password,
            name = request.name,
            birthDate = request.birthDate,
            email = request.email,
        )
        return ApiResponse.success(UserV1Dto.SignUpResponse.from(info))
    }
}
