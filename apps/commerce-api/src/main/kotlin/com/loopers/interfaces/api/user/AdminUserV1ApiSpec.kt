package com.loopers.interfaces.api.user

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Admin User V1 API", description = "Loopers admin user API.")
interface AdminUserV1ApiSpec {
    @Operation(summary = "Admin sign-up", description = "Creates a new admin user.")
    fun signUpAdmin(request: UserV1Dto.SignUpRequest): ApiResponse<UserV1Dto.MyInfoResponse>
}
