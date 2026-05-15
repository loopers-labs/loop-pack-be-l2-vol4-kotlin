package com.loopers.account.application

import com.loopers.account.domain.Account
import com.loopers.account.domain.AccountCredential
import com.loopers.account.domain.CredentialMethod
import com.loopers.account.domain.PasswordEncryptor
import com.loopers.account.domain.error.AccountErrorCode
import com.loopers.account.domain.validator.PasswordValidator
import com.loopers.account.domain.vo.AccountName
import com.loopers.account.domain.vo.CredentialIdentifier
import com.loopers.account.domain.vo.CredentialSecret
import com.loopers.account.domain.vo.Email
import com.loopers.account.persistence.AccountCredentialRepository
import com.loopers.account.persistence.AccountRepository
import com.loopers.support.error.BadRequestException
import com.loopers.support.error.ConflictException
import com.loopers.support.error.NotFoundException
import com.loopers.support.error.UnauthorizedException
import java.time.LocalDate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AccountService(
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
        if (accountRepository.existsByEmail(email)) {
            throw ConflictException(AccountErrorCode.DUPLICATE_EMAIL)
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

    @Transactional(readOnly = true)
    fun authenticate(command: AccountAuthenticateCommand): AccountAuthenticatedInfo {
        val identifier = createPasswordIdentifier(command.loginId)
        val credential = accountCredentialRepository.findBy(CredentialMethod.PASSWORD, identifier)
            ?: throw UnauthorizedException()

        if (!passwordEncryptor.matches(command.password, credential.secret.value)) {
            throw UnauthorizedException()
        }

        return AccountAuthenticatedInfo(
            accountId = credential.account.id,
            loginId = credential.identifier.value,
        )
    }

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

    @Transactional
    fun changePassword(command: AccountPasswordChangeCommand) {
        val identifier = CredentialIdentifier(CredentialMethod.PASSWORD, command.loginId)
        val credential = accountCredentialRepository.findBy(CredentialMethod.PASSWORD, identifier)
            ?: throw UnauthorizedException()

        if (!passwordEncryptor.matches(command.currentPassword, credential.secret.value)) {
            throw UnauthorizedException()
        }
        if (command.currentPassword == command.newPassword) {
            throw BadRequestException(AccountErrorCode.INVALID_PASSWORD)
        }

        PasswordValidator.validate(command.newPassword, credential.account.birthDate)

        credential.changeSecret(CredentialSecret(passwordEncryptor.encode(command.newPassword)))
        accountCredentialRepository.save(credential)
    }

    private fun validateBirthDate(birthDate: LocalDate) {
        if (birthDate.isAfter(LocalDate.now())) {
            throw BadRequestException(AccountErrorCode.INVALID_BIRTH_DATE)
        }
    }

    private fun createPasswordIdentifier(loginId: String): CredentialIdentifier =
        try {
            CredentialIdentifier(CredentialMethod.PASSWORD, loginId)
        } catch (e: BadRequestException) {
            throw UnauthorizedException()
        }
}

data class AccountCreateCommand(
    val loginId: String,
    val email: String,
    val password: String,
    val name: String,
    val birthDate: LocalDate,
)

data class AccountAuthenticateCommand(
    val loginId: String,
    val password: String,
)

data class AccountAuthenticatedInfo(
    val accountId: Long,
    val loginId: String,
)

data class AccountMeInfo(
    val loginId: String,
    val name: String,
    val birthDate: LocalDate,
    val email: String,
)

data class AccountPasswordChangeCommand(
    val loginId: String,
    val currentPassword: String,
    val newPassword: String,
)
