package com.loopers.account.application

import com.loopers.account.domain.Account
import com.loopers.account.domain.AccountCredential
import com.loopers.account.domain.AccountCredentialRepository
import com.loopers.account.domain.AccountRepository
import com.loopers.account.domain.CredentialMethod
import com.loopers.account.domain.PasswordEncryptor
import com.loopers.account.domain.error.AccountErrorCode
import com.loopers.account.domain.validator.PasswordValidator
import com.loopers.account.domain.vo.AccountName
import com.loopers.account.domain.vo.CredentialIdentifier
import com.loopers.account.domain.vo.CredentialSecret
import com.loopers.account.domain.vo.Email
import com.loopers.support.error.BadRequestException
import com.loopers.support.error.ConflictException
import java.time.LocalDate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AccountCreateService(
    private val accountRepository: AccountRepository,
    private val accountCredentialRepository: AccountCredentialRepository,
    private val passwordEncryptor: PasswordEncryptor,
) {
    @Transactional
    fun create(command: AccountCreateCommand) {
        validateBirthDate(command.birthDate)
        val identifier = CredentialIdentifier(CredentialMethod.PASSWORD, command.loginId)
        val email = Email(command.email)
        val name = AccountName(command.name)
        PasswordValidator.validate(command.password, command.birthDate)

        if (accountCredentialRepository.existsBy(CredentialMethod.PASSWORD, identifier)) {
            throw ConflictException(AccountErrorCode.DUPLICATE_LOGIN_ID)
        }

        val account = accountRepository.save(
            Account(
                name = name,
                birthDate = command.birthDate,
                email = email,
            ),
        )
        val credential = AccountCredential(
            account = account,
            method = CredentialMethod.PASSWORD,
            identifier = identifier,
            secret = CredentialSecret(passwordEncryptor.encode(command.password)),
        )
        accountCredentialRepository.save(credential)
    }

    private fun validateBirthDate(birthDate: LocalDate) {
        if (birthDate.isAfter(LocalDate.now())) {
            throw BadRequestException(AccountErrorCode.INVALID_BIRTH_DATE)
        }
    }
}

data class AccountCreateCommand(
    val loginId: String,
    val email: String,
    val password: String,
    val name: String,
    val birthDate: LocalDate,
)
