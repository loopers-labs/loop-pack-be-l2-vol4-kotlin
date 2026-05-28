package com.loopers.account.application

import com.loopers.account.domain.Account
import com.loopers.account.domain.AccountCredential
import com.loopers.account.domain.AccountRole
import com.loopers.account.domain.CredentialMethod
import com.loopers.account.domain.PasswordEncryptor
import com.loopers.account.domain.error.AccountErrorCode
import com.loopers.account.domain.vo.AccountName
import com.loopers.account.domain.vo.CredentialIdentifier
import com.loopers.account.domain.vo.CredentialSecret
import com.loopers.account.domain.vo.Email
import com.loopers.account.persistence.AccountCredentialRepository
import com.loopers.account.persistence.AccountRepository
import com.loopers.support.error.BadRequestException
import com.loopers.support.error.CommonErrorCode
import com.loopers.support.error.ConflictException
import com.loopers.support.error.NotFoundException
import com.loopers.support.error.UnauthorizedException
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
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AccountServiceTest {
    private val accountRepository: AccountRepository = mock()
    private val accountCredentialRepository: AccountCredentialRepository = mock()
    private val passwordEncryptor: PasswordEncryptor = mock()
    private val service = AccountService(
        accountRepository = accountRepository,
        accountCredentialRepository = accountCredentialRepository,
        passwordEncryptor = passwordEncryptor,
    )

    @DisplayName("유효한 회원가입 요청이면 account와 credential을 저장한다.")
    @Test
    fun savesAccountAndCredential_whenCreateRequestIsValid() {
        // given
        val command = validCreateCommand()
        whenever(accountCredentialRepository.existsBy(eq(CredentialMethod.PASSWORD), any()))
            .thenReturn(false)
        whenever(passwordEncryptor.encode(command.password)).thenReturn(ENCODED_PASSWORD)
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
            { assertThat(savedCredential.secret.value).isEqualTo(ENCODED_PASSWORD) },
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

    @DisplayName("이미 가입된 이메일이면 CONFLICT 예외가 발생하고 저장하지 않는다.")
    @Test
    fun throwsConflictAndDoesNotSave_whenEmailAlreadyExists() {
        // given
        val command = validCreateCommand()
        whenever(accountCredentialRepository.existsBy(eq(CredentialMethod.PASSWORD), any())).thenReturn(false)
        whenever(accountRepository.existsByEmail(any())).thenReturn(true)

        // when
        val result = assertThrows<ConflictException> {
            service.create(command)
        }

        // then
        assertAll(
            { assertThat(result.errorCode).isEqualTo(AccountErrorCode.DUPLICATE_EMAIL) },
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
        assertThat(result.errorCode).isEqualTo(AccountErrorCode.INVALID_BIRTH_DATE)
    }

    @DisplayName("로그인 ID 형식이 유효하지 않으면 BAD_REQUEST 예외가 발생한다.")
    @Test
    fun throwsBadRequest_whenLoginIdIsInvalidForCreate() {
        // given
        val command = validCreateCommand(loginId = INVALID_LOGIN_ID)

        // when
        val result = assertThrows<BadRequestException> {
            service.create(command)
        }

        // then
        assertThat(result.errorCode).isEqualTo(AccountErrorCode.INVALID_CREDENTIAL_IDENTIFIER)
    }

    @DisplayName("이메일 형식이 유효하지 않으면 BAD_REQUEST 예외가 발생한다.")
    @Test
    fun throwsBadRequest_whenEmailIsInvalid() {
        // given
        val command = validCreateCommand(email = INVALID_EMAIL)

        // when
        val result = assertThrows<BadRequestException> {
            service.create(command)
        }

        // then
        assertThat(result.errorCode).isEqualTo(AccountErrorCode.INVALID_EMAIL)
    }

    @DisplayName("비밀번호 형식이 유효하지 않으면 BAD_REQUEST 예외가 발생한다.")
    @Test
    fun throwsBadRequest_whenPasswordIsInvalid() {
        // given
        val command = validCreateCommand(password = INVALID_PASSWORD)

        // when
        val result = assertThrows<BadRequestException> {
            service.create(command)
        }

        // then
        assertThat(result.errorCode).isEqualTo(AccountErrorCode.INVALID_PASSWORD)
    }

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
            { assertThat(result.role).isEqualTo(AccountRole.USER) },
        )
    }

    @DisplayName("admin 권한 account가 인증되면 role=ADMIN이 함께 반환된다.")
    @Test
    fun returnsAdminRole_whenAccountIsAdmin() {
        // given
        val command = AccountAuthenticateCommand(
            loginId = LOGIN_ID,
            password = RAW_PASSWORD,
        )
        val credential = createCredential(command.loginId, ENCODED_PASSWORD, AccountRole.ADMIN)
        whenever(accountCredentialRepository.findBy(eq(CredentialMethod.PASSWORD), any()))
            .thenReturn(credential)
        whenever(passwordEncryptor.matches(command.password, credential.secret.value))
            .thenReturn(true)

        // when
        val result = service.authenticate(command)

        // then
        assertThat(result.role).isEqualTo(AccountRole.ADMIN)
    }

    @DisplayName("로그인 ID의 credential이 없으면 인증 시 UNAUTHORIZED 예외가 발생한다.")
    @Test
    fun throwsUnauthorized_whenCredentialDoesNotExistForAuthenticate() {
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

    @DisplayName("credential은 존재하지만 비밀번호가 일치하지 않으면 인증 시 UNAUTHORIZED 예외가 발생한다.")
    @Test
    fun throwsUnauthorized_whenPasswordDoesNotMatchForAuthenticate() {
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

    @DisplayName("로그인 ID 형식이 인증용 credential 식별자로 유효하지 않으면 BAD_REQUEST 예외가 발생한다.")
    @Test
    fun throwsBadRequest_whenLoginIdIsInvalidForAuthenticate() {
        // given
        val command = AccountAuthenticateCommand(
            loginId = INVALID_LOGIN_ID,
            password = RAW_PASSWORD,
        )

        // when
        val result = assertThrows<BadRequestException> {
            service.authenticate(command)
        }

        // then
        assertAll(
            { assertThat(result.errorCode).isEqualTo(AccountErrorCode.INVALID_CREDENTIAL_IDENTIFIER) },
            { verify(accountCredentialRepository, never()).findBy(any(), any()) },
            { verify(passwordEncryptor, never()).matches(any(), any()) },
        )
    }

    @DisplayName("인증된 account ID와 로그인 ID가 주어지면 내 정보를 반환한다.")
    @Test
    fun returnsMyAccountInfo_whenAuthenticatedAccountExists() {
        // given
        val account = createAccount()
        whenever(accountRepository.findById(ACCOUNT_ID)).thenReturn(account)

        // when
        val result = service.getMe(ACCOUNT_ID, LOGIN_ID)

        // then
        assertAll(
            { assertThat(result.loginId).isEqualTo(LOGIN_ID) },
            { assertThat(result.name).isEqualTo(MASKED_ACCOUNT_NAME) },
            { assertThat(result.birthDate).isEqualTo(BIRTH_DATE) },
            { assertThat(result.email).isEqualTo(EMAIL) },
        )
    }

    @DisplayName("인증된 account ID에 해당하는 계정이 없으면 NOT_FOUND 예외가 발생한다.")
    @Test
    fun throwsNotFound_whenAuthenticatedAccountDoesNotExist() {
        // given
        whenever(accountRepository.findById(ACCOUNT_ID)).thenReturn(null)

        // when
        val result = assertThrows<NotFoundException> {
            service.getMe(ACCOUNT_ID, LOGIN_ID)
        }

        // then
        assertThat(result.errorCode).isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND)
    }

    @DisplayName("기존 비밀번호가 일치하고 새 비밀번호가 유효하면 credential secret을 변경한다.")
    @Test
    fun changesCredentialSecret_whenCurrentPasswordMatchesAndNewPasswordIsValid() {
        // given
        val command = validPasswordChangeCommand()
        val credential = createCredential(command.loginId, ENCODED_CURRENT_PASSWORD)
        whenever(accountCredentialRepository.findBy(eq(CredentialMethod.PASSWORD), any()))
            .thenReturn(credential)
        whenever(passwordEncryptor.matches(command.currentPassword, ENCODED_CURRENT_PASSWORD))
            .thenReturn(true)
        whenever(passwordEncryptor.encode(command.newPassword))
            .thenReturn(ENCODED_NEW_PASSWORD)

        // when
        service.changePassword(command)

        // then
        assertAll(
            { assertThat(credential.secret.value).isEqualTo(ENCODED_NEW_PASSWORD) },
            { verify(accountCredentialRepository).save(same(credential)) },
        )
    }

    @DisplayName("로그인 ID의 credential이 없으면 비밀번호 변경 시 UNAUTHORIZED 예외가 발생한다.")
    @Test
    fun throwsUnauthorized_whenCredentialDoesNotExistForPasswordChange() {
        // given
        val command = validPasswordChangeCommand()
        whenever(accountCredentialRepository.findBy(eq(CredentialMethod.PASSWORD), any()))
            .thenReturn(null)

        // when
        val result = assertThrows<UnauthorizedException> {
            service.changePassword(command)
        }

        // then
        assertAll(
            { assertThat(result.errorCode).isEqualTo(CommonErrorCode.UNAUTHORIZED) },
            { verify(passwordEncryptor, never()).matches(any(), any()) },
            { verify(accountCredentialRepository, never()).save(any()) },
        )
    }

    @DisplayName("기존 비밀번호가 일치하지 않으면 비밀번호 변경 시 UNAUTHORIZED 예외가 발생한다.")
    @Test
    fun throwsUnauthorized_whenCurrentPasswordDoesNotMatch() {
        // given
        val command = validPasswordChangeCommand(currentPassword = WRONG_PASSWORD)
        val credential = createCredential(command.loginId, ENCODED_CURRENT_PASSWORD)
        whenever(accountCredentialRepository.findBy(eq(CredentialMethod.PASSWORD), any()))
            .thenReturn(credential)
        whenever(passwordEncryptor.matches(command.currentPassword, ENCODED_CURRENT_PASSWORD))
            .thenReturn(false)

        // when
        val result = assertThrows<UnauthorizedException> {
            service.changePassword(command)
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
        val command = validPasswordChangeCommand(newPassword = INVALID_PASSWORD)
        val credential = createCredential(command.loginId, ENCODED_CURRENT_PASSWORD)
        whenever(accountCredentialRepository.findBy(eq(CredentialMethod.PASSWORD), any()))
            .thenReturn(credential)
        whenever(passwordEncryptor.matches(command.currentPassword, ENCODED_CURRENT_PASSWORD))
            .thenReturn(true)

        // when
        val result = assertThrows<BadRequestException> {
            service.changePassword(command)
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
        val command = validPasswordChangeCommand(newPassword = RAW_PASSWORD)
        val credential = createCredential(command.loginId, ENCODED_CURRENT_PASSWORD)
        whenever(accountCredentialRepository.findBy(eq(CredentialMethod.PASSWORD), any()))
            .thenReturn(credential)
        whenever(passwordEncryptor.matches(command.currentPassword, ENCODED_CURRENT_PASSWORD))
            .thenReturn(true)

        // when
        val result = assertThrows<BadRequestException> {
            service.changePassword(command)
        }

        // then
        assertAll(
            { assertThat(result.errorCode).isEqualTo(AccountErrorCode.INVALID_PASSWORD) },
            { verify(passwordEncryptor, never()).encode(any()) },
            { verify(accountCredentialRepository, never()).save(any()) },
        )
    }

    private fun validCreateCommand(
        loginId: String = LOGIN_ID,
        email: String = EMAIL,
        password: String = RAW_PASSWORD,
        name: String = ACCOUNT_NAME,
        birthDate: LocalDate = BIRTH_DATE,
    ): AccountCreateCommand =
        AccountCreateCommand(
            loginId = loginId,
            email = email,
            password = password,
            name = name,
            birthDate = birthDate,
        )

    private fun validPasswordChangeCommand(
        loginId: String = LOGIN_ID,
        currentPassword: String = RAW_PASSWORD,
        newPassword: String = NEW_PASSWORD,
    ): AccountPasswordChangeCommand =
        AccountPasswordChangeCommand(
            loginId = loginId,
            currentPassword = currentPassword,
            newPassword = newPassword,
        )

    private fun createAccount(role: AccountRole = AccountRole.USER): Account =
        Account(
            name = AccountName(ACCOUNT_NAME),
            birthDate = BIRTH_DATE,
            email = Email(EMAIL),
            role = role,
        )

    private fun createCredential(
        loginId: String,
        encodedPassword: String,
        role: AccountRole = AccountRole.USER,
    ): AccountCredential =
        AccountCredential(
            account = createAccount(role),
            method = CredentialMethod.PASSWORD,
            identifier = CredentialIdentifier(CredentialMethod.PASSWORD, loginId),
            secret = CredentialSecret(encodedPassword),
        )

    private companion object {
        private const val ACCOUNT_ID = 1L
        private const val LOGIN_ID = "shoeone96"
        private const val INVALID_LOGIN_ID = "shoeone!"
        private const val EMAIL = "user@example.com"
        private const val INVALID_EMAIL = "user@"
        private const val RAW_PASSWORD = "abf15!@#"
        private const val NEW_PASSWORD = "cdg26!@#"
        private const val WRONG_PASSWORD = "wrong15!@#"
        private const val INVALID_PASSWORD = "abc123!"
        private const val ENCODED_PASSWORD = "encoded-password"
        private const val ENCODED_CURRENT_PASSWORD = "encoded-current-password"
        private const val ENCODED_NEW_PASSWORD = "encoded-new-password"
        private const val ACCOUNT_NAME = "홍길동"
        private const val MASKED_ACCOUNT_NAME = "홍길*"
        private val BIRTH_DATE: LocalDate = LocalDate.of(1996, 1, 1)
    }
}
