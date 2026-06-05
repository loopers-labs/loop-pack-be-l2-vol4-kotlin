package com.loopers.domain.user.presentation.request

import com.loopers.domain.user.application.command.UserSignUpCommand
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Past
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDate

data class SignUpRequest(
    @field:NotBlank(message = "로그인 ID 는 필수입니다.")
    @field:Pattern(regexp = "^[A-Za-z0-9]{4,20}$", message = "로그인 ID 는 영문/숫자 4~20자여야 합니다.")
    val loginId: String,
    @field:NotBlank(message = "비밀번호는 필수입니다.")
    @field:Size(min = 8, max = 16, message = "비밀번호는 8~16자여야 합니다.")
    val password: String,
    @field:NotBlank(message = "이름은 필수입니다.")
    @field:Size(min = 1, max = 50, message = "이름은 1~50자여야 합니다.")
    val name: String,
    @field:NotNull(message = "생년월일은 필수입니다.")
    @field:Past(message = "생년월일은 과거 일자여야 합니다.")
    val birthday: LocalDate,
    @field:NotBlank(message = "이메일은 필수입니다.")
    @field:Email(message = "이메일 형식이 올바르지 않습니다.")
    val email: String,
) {
    fun toCommand(): UserSignUpCommand = UserSignUpCommand(
        loginId = loginId,
        rawPassword = password,
        name = name,
        birthday = birthday,
        email = email,
    )
}
