package com.loopers.account.application

import com.loopers.account.domain.CredentialMethod
import com.loopers.account.domain.PasswordEncryptor
import com.loopers.account.domain.error.AccountErrorCode
import com.loopers.account.domain.validator.PasswordValidator
import com.loopers.account.domain.vo.CredentialIdentifier
import com.loopers.account.domain.vo.CredentialSecret
import com.loopers.account.persistence.AccountCredentialRepository
import com.loopers.support.error.BadRequestException
import com.loopers.support.error.UnauthorizedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AccountPasswordChangeService(
    private val accountCredentialRepository: AccountCredentialRepository,
    private val passwordEncryptor: PasswordEncryptor,
) {
    @Transactional
    fun change(command: AccountPasswordChangeCommand) {
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
}

data class AccountPasswordChangeCommand(
    val loginId: String,
    val currentPassword: String,
    val newPassword: String,
)
