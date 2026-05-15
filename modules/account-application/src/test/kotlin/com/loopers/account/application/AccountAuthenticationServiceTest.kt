package com.loopers.account.application

import com.loopers.account.domain.Account
import com.loopers.account.domain.AccountCredential
import com.loopers.account.domain.AccountCredentialRepository
import com.loopers.account.domain.CredentialMethod
import com.loopers.account.domain.PasswordEncryptor
import com.loopers.account.domain.vo.AccountName
import com.loopers.account.domain.vo.CredentialIdentifier
import com.loopers.account.domain.vo.CredentialSecret
import com.loopers.account.domain.vo.Email
import com.loopers.support.error.CommonErrorCode
import com.loopers.support.error.UnauthorizedException
import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AccountAuthenticationServiceTest {
    private val accountCredentialRepository: AccountCredentialRepository = mock()
    private val passwordEncryptor: PasswordEncryptor = mock()
    private val service = AccountAuthenticationService(
        accountCredentialRepository = accountCredentialRepository,
        passwordEncryptor = passwordEncryptor,
    )

    @DisplayName("로그인 ID의 credential이 존재하고 비밀번호가 일치하면 인증된 account 정보를 반환한다.")
    @Test
    fun returnsAuthenticatedAccount_whenCredentialExistsAndPasswordMatches() {
        // given
        val command = AccountAuthenticateCommand(
            loginId = LOGIN_ID,
            password = RAW_PASSWORD,
        )
        val credential = createCredential(command.loginId, ENCODED_PASSWORD)
        whenever(accountCredentialRepository.findBy(eq(CredentialMethod.PASSWORD), any()))
            .thenReturn(credential)
        whenever(passwordEncryptor.matches(command.password, credential.secret.value))
            .thenReturn(true)

        // when
        val result = service.authenticate(command)

        // then
        assertAll(
            { assertThat(result.accountId).isEqualTo(credential.account.id) },
            { assertThat(result.loginId).isEqualTo(command.loginId) },
        )
    }

    @DisplayName("로그인 ID의 credential이 없으면 UNAUTHORIZED 예외가 발생한다.")
    @Test
    fun throwsUnauthorized_whenCredentialDoesNotExist() {
        // given
        val command = AccountAuthenticateCommand(
            loginId = LOGIN_ID,
            password = RAW_PASSWORD,
        )
        whenever(accountCredentialRepository.findBy(eq(CredentialMethod.PASSWORD), any()))
            .thenReturn(null)

        // when
        val result = assertThrows<UnauthorizedException> {
            service.authenticate(command)
        }

        // then
        assertAll(
            { assertThat(result.errorCode).isEqualTo(CommonErrorCode.UNAUTHORIZED) },
            { verify(passwordEncryptor, never()).matches(any(), any()) },
        )
    }

    @DisplayName("credential은 존재하지만 비밀번호가 일치하지 않으면 UNAUTHORIZED 예외가 발생한다.")
    @Test
    fun throwsUnauthorized_whenPasswordDoesNotMatch() {
        // given
        val command = AccountAuthenticateCommand(
            loginId = LOGIN_ID,
            password = WRONG_PASSWORD,
        )
        val credential = createCredential(command.loginId, ENCODED_PASSWORD)
        whenever(accountCredentialRepository.findBy(eq(CredentialMethod.PASSWORD), any()))
            .thenReturn(credential)
        whenever(passwordEncryptor.matches(command.password, credential.secret.value))
            .thenReturn(false)

        // when
        val result = assertThrows<UnauthorizedException> {
            service.authenticate(command)
        }

        // then
        assertThat(result.errorCode).isEqualTo(CommonErrorCode.UNAUTHORIZED)
    }

    @DisplayName("로그인 ID 형식이 인증용 credential 식별자로 유효하지 않으면 UNAUTHORIZED 예외가 발생한다.")
    @Test
    fun throwsUnauthorized_whenLoginIdIsInvalid() {
        // given
        val command = AccountAuthenticateCommand(
            loginId = INVALID_LOGIN_ID,
            password = RAW_PASSWORD,
        )

        // when
        val result = assertThrows<UnauthorizedException> {
            service.authenticate(command)
        }

        // then
        assertAll(
            { assertThat(result.errorCode).isEqualTo(CommonErrorCode.UNAUTHORIZED) },
            { verify(accountCredentialRepository, never()).findBy(any(), any()) },
            { verify(passwordEncryptor, never()).matches(any(), any()) },
        )
    }

    private fun createCredential(
        loginId: String,
        encodedPassword: String,
    ): AccountCredential {
        val account = Account(
            name = AccountName(ACCOUNT_NAME),
            birthDate = BIRTH_DATE,
            email = Email(EMAIL),
        )
        return AccountCredential(
            account = account,
            method = CredentialMethod.PASSWORD,
            identifier = CredentialIdentifier(CredentialMethod.PASSWORD, loginId),
            secret = CredentialSecret(encodedPassword),
        )
    }

    private companion object {
        private const val LOGIN_ID = "shoeone96"
        private const val INVALID_LOGIN_ID = "shoeone!"
        private const val RAW_PASSWORD = "abf15!@#"
        private const val WRONG_PASSWORD = "wrong15!@#"
        private const val ENCODED_PASSWORD = "encoded-password"
        private const val ACCOUNT_NAME = "홍길동"
        private const val EMAIL = "user@example.com"
        private val BIRTH_DATE: LocalDate = LocalDate.of(1996, 1, 1)
    }
}
