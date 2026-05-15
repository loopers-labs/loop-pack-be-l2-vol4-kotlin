package com.loopers.account.application

import com.loopers.account.domain.error.AccountErrorCode
import com.loopers.account.persistence.AccountRepository
import com.loopers.support.error.NotFoundException
import java.time.LocalDate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AccountMeService(
    private val accountRepository: AccountRepository,
) {
    @Transactional(readOnly = true)
    fun getMe(
        accountId: Long,
        loginId: String,
    ): AccountMeInfo {
        val account = accountRepository.findById(accountId)
            ?: throw NotFoundException(AccountErrorCode.ACCOUNT_NOT_FOUND)

        return AccountMeInfo(
            loginId = loginId,
            name = account.maskedName(),
            birthDate = account.birthDate,
            email = account.email.value,
        )
    }
}

data class AccountMeInfo(
    val loginId: String,
    val name: String,
    val birthDate: LocalDate,
    val email: String,
)
