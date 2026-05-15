package com.loopers.account.application

import com.loopers.account.domain.CredentialMethod
import com.loopers.account.domain.PasswordEncryptor
import com.loopers.account.domain.vo.CredentialIdentifier
import com.loopers.account.persistence.AccountCredentialRepository
import com.loopers.support.error.BadRequestException
import com.loopers.support.error.UnauthorizedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AccountAuthenticationService(
    private val accountCredentialRepository: AccountCredentialRepository,
    private val passwordEncryptor: PasswordEncryptor,
) {
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

    private fun createPasswordIdentifier(loginId: String): CredentialIdentifier =
        try {
            CredentialIdentifier(CredentialMethod.PASSWORD, loginId)
        } catch (e: BadRequestException) {
            throw UnauthorizedException()
        }
}

data class AccountAuthenticateCommand(
    val loginId: String,
    val password: String,
)

data class AccountAuthenticatedInfo(
    val accountId: Long,
    val loginId: String,
)
