package com.loopers.account.interfaces

import com.loopers.account.application.AccountCreateCommand
import com.loopers.account.application.AccountMeInfo
import com.loopers.account.application.AccountPasswordChangeCommand
import com.loopers.account.application.AccountService
import com.loopers.account.infrastructure.security.AccountAuthenticationAttributes
import java.time.LocalDate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class AccountController(
    private val accountService: AccountService,
) {
    @PostMapping
    fun createAccount(
        @RequestBody request: AccountCreateRequest,
    ) {
        accountService.create(request.toCommand())
    }

    @GetMapping("/me")
    fun getMe(
        @RequestAttribute(AccountAuthenticationAttributes.ACCOUNT_ID) accountId: Long,
        @RequestAttribute(AccountAuthenticationAttributes.LOGIN_ID) loginId: String,
    ): AccountMeResponse =
        accountService.getMe(accountId, loginId).toResponse()

    @PutMapping("/password")
    fun changePassword(
        @RequestAttribute(AccountAuthenticationAttributes.LOGIN_ID) loginId: String,
        @RequestBody request: AccountPasswordChangeRequest,
    ) {
        accountService.changePassword(request.toCommand(loginId))
    }
}

data class AccountCreateRequest(
    val loginId: String,
    val email: String,
    val password: String,
    val name: String,
    val birthDate: LocalDate,
) {
    fun toCommand(): AccountCreateCommand =
        AccountCreateCommand(
            loginId = loginId,
            email = email,
            password = password,
            name = name,
            birthDate = birthDate,
        )
}

data class AccountMeResponse(
    val loginId: String,
    val name: String,
    val birthDate: LocalDate,
    val email: String,
)

data class AccountPasswordChangeRequest(
    val currentPassword: String,
    val newPassword: String,
) {
    fun toCommand(loginId: String): AccountPasswordChangeCommand =
        AccountPasswordChangeCommand(
            loginId = loginId,
            currentPassword = currentPassword,
            newPassword = newPassword,
        )
}

private fun AccountMeInfo.toResponse(): AccountMeResponse =
    AccountMeResponse(
        loginId = loginId,
        name = name,
        birthDate = birthDate,
        email = email,
    )
