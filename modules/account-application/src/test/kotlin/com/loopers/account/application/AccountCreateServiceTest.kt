package com.loopers.account.application

import com.loopers.account.domain.Account
import com.loopers.account.domain.AccountCredential
import com.loopers.account.domain.AccountCredentialRepository
import com.loopers.account.domain.AccountRepository
import com.loopers.account.domain.CredentialMethod
import com.loopers.account.domain.PasswordEncryptor
import com.loopers.account.domain.error.AccountErrorCode
import com.loopers.support.error.BadRequestException
import com.loopers.support.error.ConflictException
import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AccountCreateServiceTest {
    private val accountRepository: AccountRepository = mock()
    private val accountCredentialRepository: AccountCredentialRepository = mock()
    private val passwordEncryptor: PasswordEncryptor = mock()
    private val service = AccountCreateService(
        accountRepository = accountRepository,
        accountCredentialRepository = accountCredentialRepository,
        passwordEncryptor = passwordEncryptor,
    )

    @DisplayName("유효한 회원가입 요청이면 account와 credential을 저장한다.")
    @Test
    fun savesAccountAndCredential_whenRequestIsValid() {
        // given
        val command = validCreateCommand()
        whenever(accountCredentialRepository.existsBy(eq(CredentialMethod.PASSWORD), any()))
            .thenReturn(false)
        whenever(passwordEncryptor.encode(command.password)).thenReturn("encoded-password")
        whenever(accountRepository.save(any<Account>())).thenAnswer { invocation -> invocation.arguments[0] as Account }
        whenever(accountCredentialRepository.save(any<AccountCredential>()))
            .thenAnswer { invocation -> invocation.arguments[0] as AccountCredential }

        // when
        service.create(command)

        // then
        val accountCaptor = argumentCaptor<Account>()
        val credentialCaptor = argumentCaptor<AccountCredential>()
        verify(accountRepository).save(accountCaptor.capture())
        verify(accountCredentialRepository).save(credentialCaptor.capture())
        val savedAccount = accountCaptor.firstValue
        val savedCredential = credentialCaptor.firstValue
        assertAll(
            { assertThat(savedAccount.name.value).isEqualTo(command.name) },
            { assertThat(savedAccount.birthDate).isEqualTo(command.birthDate) },
            { assertThat(savedAccount.email.value).isEqualTo(command.email) },
            { assertThat(savedCredential.account).isSameAs(savedAccount) },
            { assertThat(savedCredential.method).isEqualTo(CredentialMethod.PASSWORD) },
            { assertThat(savedCredential.identifier.value).isEqualTo(command.loginId) },
            { assertThat(savedCredential.secret.value).isEqualTo("encoded-password") },
        )
    }

    @DisplayName("이미 가입된 로그인 ID이면 CONFLICT 예외가 발생한다.")
    @Test
    fun throwsConflict_whenLoginIdAlreadyExists() {
        // given
        val command = validCreateCommand()
        whenever(accountCredentialRepository.existsBy(eq(CredentialMethod.PASSWORD), any())).thenReturn(true)

        // when
        val result = assertThrows<ConflictException> {
            service.create(command)
        }

        // then
        assertAll(
            { assertThat(result.errorCode).isEqualTo(AccountErrorCode.DUPLICATE_LOGIN_ID) },
            { verify(accountRepository, never()).save(any<Account>()) },
            { verify(accountCredentialRepository, never()).save(any<AccountCredential>()) },
        )
    }

    @DisplayName("미래 생년월일이면 BAD_REQUEST 예외가 발생한다.")
    @Test
    fun throwsBadRequest_whenBirthDateIsFuture() {
        // given
        val command = validCreateCommand(birthDate = LocalDate.now().plusDays(1))

        // when
        val result = assertThrows<BadRequestException> {
            service.create(command)
        }

        // then
        assertAll(
            { assertThat(result.errorCode).isEqualTo(AccountErrorCode.INVALID_BIRTH_DATE) },
        )
    }

    @DisplayName("로그인 ID 형식이 유효하지 않으면 BAD_REQUEST 예외가 발생한다.")
    @Test
    fun throwsBadRequest_whenLoginIdIsInvalid() {
        // given
        val command = validCreateCommand(loginId = "shoeone!")

        // when
        val result = assertThrows<BadRequestException> {
            service.create(command)
        }

        // then
        assertAll(
            { assertThat(result.errorCode).isEqualTo(AccountErrorCode.INVALID_CREDENTIAL_IDENTIFIER) },
        )
    }

    @DisplayName("이메일 형식이 유효하지 않으면 BAD_REQUEST 예외가 발생한다.")
    @Test
    fun throwsBadRequest_whenEmailIsInvalid() {
        // given
        val command = validCreateCommand(email = "user@")

        // when
        val result = assertThrows<BadRequestException> {
            service.create(command)
        }

        // then
        assertAll(
            { assertThat(result.errorCode).isEqualTo(AccountErrorCode.INVALID_EMAIL) },
        )
    }

    @DisplayName("비밀번호 형식이 유효하지 않으면 BAD_REQUEST 예외가 발생한다.")
    @Test
    fun throwsBadRequest_whenPasswordIsInvalid() {
        // given
        val command = validCreateCommand(password = "abc123!")

        // when
        val result = assertThrows<BadRequestException> {
            service.create(command)
        }

        // then
        assertAll(
            { assertThat(result.errorCode).isEqualTo(AccountErrorCode.INVALID_PASSWORD) },
        )
    }

    private fun validCreateCommand(
        loginId: String = "shoeone96",
        email: String = "user@example.com",
        password: String = "abf15!@#",
        name: String = "홍길동",
        birthDate: LocalDate = LocalDate.of(1996, 1, 1),
    ): AccountCreateCommand =
        AccountCreateCommand(
            loginId = loginId,
            email = email,
            password = password,
            name = name,
            birthDate = birthDate,
        )
}
