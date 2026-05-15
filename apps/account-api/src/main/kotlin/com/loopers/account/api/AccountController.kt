package com.loopers.account.api

import com.loopers.account.application.AccountCreateCommand
import com.loopers.account.application.AccountCreateService
import com.loopers.account.application.AccountMeInfo
import com.loopers.account.application.AccountMeService
import com.loopers.account.application.AccountPasswordChangeCommand
import com.loopers.account.application.AccountPasswordChangeService
import com.loopers.account.security.AccountAuthenticationAttributes
import java.time.LocalDate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/accounts")
class AccountController(
    private val accountCreateService: AccountCreateService,
    private val accountMeService: AccountMeService,
    private val accountPasswordChangeService: AccountPasswordChangeService,
) {
    @PostMapping
    fun createAccount(
        @RequestBody request: AccountCreateRequest,
    ) {
        accountCreateService.create(request.toCommand())
    }

    @GetMapping("/me")
    fun getMe(
        @RequestAttribute(AccountAuthenticationAttributes.ACCOUNT_ID) accountId: Long,
        @RequestAttribute(AccountAuthenticationAttributes.LOGIN_ID) loginId: String,
    ): AccountMeResponse =
        accountMeService.getMe(accountId, loginId).toResponse()

    @PatchMapping("/me/password")
    fun changePassword(
        @RequestAttribute(AccountAuthenticationAttributes.LOGIN_ID) loginId: String,
        @RequestBody request: AccountPasswordChangeRequest,
    ) {
        accountPasswordChangeService.change(request.toCommand(loginId))
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
