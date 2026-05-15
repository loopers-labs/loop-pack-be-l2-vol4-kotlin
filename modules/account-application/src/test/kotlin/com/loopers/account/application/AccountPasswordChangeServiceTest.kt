package com.loopers.account.application

import com.loopers.account.domain.Account
import com.loopers.account.domain.AccountCredential
import com.loopers.account.domain.AccountCredentialRepository
import com.loopers.account.domain.CredentialMethod
import com.loopers.account.domain.PasswordEncryptor
import com.loopers.account.domain.error.AccountErrorCode
import com.loopers.account.domain.vo.AccountName
import com.loopers.account.domain.vo.CredentialIdentifier
import com.loopers.account.domain.vo.CredentialSecret
import com.loopers.account.domain.vo.Email
import com.loopers.support.error.BadRequestException
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
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AccountPasswordChangeServiceTest {
    private val accountCredentialRepository: AccountCredentialRepository = mock()
    private val passwordEncryptor: PasswordEncryptor = mock()
    private val service = AccountPasswordChangeService(
        accountCredentialRepository = accountCredentialRepository,
        passwordEncryptor = passwordEncryptor,
    )

    @DisplayName("기존 비밀번호가 일치하고 새 비밀번호가 유효하면 credential secret을 변경한다.")
    @Test
    fun changesCredentialSecret_whenCurrentPasswordMatchesAndNewPasswordIsValid() {
        // given
        val command = validCommand()
        val credential = createCredential(command.loginId, ENCODED_CURRENT_PASSWORD)
        whenever(accountCredentialRepository.findBy(eq(CredentialMethod.PASSWORD), any()))
            .thenReturn(credential)
        whenever(passwordEncryptor.matches(command.currentPassword, ENCODED_CURRENT_PASSWORD))
            .thenReturn(true)
        whenever(passwordEncryptor.encode(command.newPassword))
            .thenReturn(ENCODED_NEW_PASSWORD)

        // when
        service.change(command)

        // then
        assertAll(
            { assertThat(credential.secret.value).isEqualTo(ENCODED_NEW_PASSWORD) },
            { verify(accountCredentialRepository).save(same(credential)) },
        )
    }

    @DisplayName("로그인 ID의 credential이 없으면 UNAUTHORIZED 예외가 발생한다.")
    @Test
    fun throwsUnauthorized_whenCredentialDoesNotExist() {
        // given
        val command = validCommand()
        whenever(accountCredentialRepository.findBy(eq(CredentialMethod.PASSWORD), any()))
            .thenReturn(null)

        // when
        val result = assertThrows<UnauthorizedException> {
            service.change(command)
        }

        // then
        assertAll(
            { assertThat(result.errorCode).isEqualTo(CommonErrorCode.UNAUTHORIZED) },
            { verify(passwordEncryptor, never()).matches(any(), any()) },
            { verify(accountCredentialRepository, never()).save(any()) },
        )
    }

    @DisplayName("기존 비밀번호가 일치하지 않으면 UNAUTHORIZED 예외가 발생한다.")
    @Test
    fun throwsUnauthorized_whenCurrentPasswordDoesNotMatch() {
        // given
        val command = validCommand(currentPassword = WRONG_CURRENT_PASSWORD)
        val credential = createCredential(command.loginId, ENCODED_CURRENT_PASSWORD)
        whenever(accountCredentialRepository.findBy(eq(CredentialMethod.PASSWORD), any()))
            .thenReturn(credential)
        whenever(passwordEncryptor.matches(command.currentPassword, ENCODED_CURRENT_PASSWORD))
            .thenReturn(false)

        // when
        val result = assertThrows<UnauthorizedException> {
            service.change(command)
        }

        // then
        assertAll(
            { assertThat(result.errorCode).isEqualTo(CommonErrorCode.UNAUTHORIZED) },
            { verify(passwordEncryptor, never()).encode(any()) },
            { verify(accountCredentialRepository, never()).save(any()) },
        )
    }

    @DisplayName("새 비밀번호가 password rule에 맞지 않으면 BAD_REQUEST 예외가 발생한다.")
    @Test
    fun throwsBadRequest_whenNewPasswordIsInvalid() {
        // given
        val command = validCommand(newPassword = INVALID_PASSWORD)
        val credential = createCredential(command.loginId, ENCODED_CURRENT_PASSWORD)
        whenever(accountCredentialRepository.findBy(eq(CredentialMethod.PASSWORD), any()))
            .thenReturn(credential)
        whenever(passwordEncryptor.matches(command.currentPassword, ENCODED_CURRENT_PASSWORD))
            .thenReturn(true)

        // when
        val result = assertThrows<BadRequestException> {
            service.change(command)
        }

        // then
        assertAll(
            { assertThat(result.errorCode).isEqualTo(AccountErrorCode.INVALID_PASSWORD) },
            { verify(passwordEncryptor, never()).encode(any()) },
            { verify(accountCredentialRepository, never()).save(any()) },
        )
    }

    @DisplayName("새 비밀번호가 현재 비밀번호와 같으면 BAD_REQUEST 예외가 발생한다.")
    @Test
    fun throwsBadRequest_whenNewPasswordIsSameAsCurrentPassword() {
        // given
        val command = validCommand(newPassword = CURRENT_PASSWORD)
        val credential = createCredential(command.loginId, ENCODED_CURRENT_PASSWORD)
        whenever(accountCredentialRepository.findBy(eq(CredentialMethod.PASSWORD), any()))
            .thenReturn(credential)
        whenever(passwordEncryptor.matches(command.currentPassword, ENCODED_CURRENT_PASSWORD))
            .thenReturn(true)

        // when
        val result = assertThrows<BadRequestException> {
            service.change(command)
        }

        // then
        assertAll(
            { assertThat(result.errorCode).isEqualTo(AccountErrorCode.INVALID_PASSWORD) },
            { verify(passwordEncryptor, never()).encode(any()) },
            { verify(accountCredentialRepository, never()).save(any()) },
        )
    }

    private fun validCommand(
        loginId: String = LOGIN_ID,
        currentPassword: String = CURRENT_PASSWORD,
        newPassword: String = NEW_PASSWORD,
    ): AccountPasswordChangeCommand =
        AccountPasswordChangeCommand(
            loginId = loginId,
            currentPassword = currentPassword,
            newPassword = newPassword,
        )

    private fun createCredential(
        loginId: String,
        encodedPassword: String,
    ): AccountCredential =
        AccountCredential(
            account = Account(
                name = AccountName(ACCOUNT_NAME),
                birthDate = BIRTH_DATE,
                email = Email(EMAIL),
            ),
            method = CredentialMethod.PASSWORD,
            identifier = CredentialIdentifier(CredentialMethod.PASSWORD, loginId),
            secret = CredentialSecret(encodedPassword),
        )

    private companion object {
        private const val LOGIN_ID = "shoeone96"
        private const val CURRENT_PASSWORD = "abf15!@#"
        private const val NEW_PASSWORD = "cdg26!@#"
        private const val WRONG_CURRENT_PASSWORD = "wrong15!@#"
        private const val INVALID_PASSWORD = "abc123!"
        private const val ENCODED_CURRENT_PASSWORD = "encoded-current-password"
        private const val ENCODED_NEW_PASSWORD = "encoded-new-password"
        private const val ACCOUNT_NAME = "홍길동"
        private const val EMAIL = "user@example.com"
        private val BIRTH_DATE: LocalDate = LocalDate.of(1996, 1, 1)
    }
}
