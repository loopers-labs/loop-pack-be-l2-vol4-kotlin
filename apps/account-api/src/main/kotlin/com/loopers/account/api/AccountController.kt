package com.loopers.account.api

import com.loopers.account.application.AccountCreateCommand
import com.loopers.account.application.AccountCreateService
import java.time.LocalDate
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/accounts")
class AccountController(
    private val accountCreateService: AccountCreateService,
) {
    @PostMapping
    fun createAccount(
        @RequestBody request: AccountCreateRequest,
    ) {
        accountCreateService.create(request.toCommand())
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
