package com.loopers.account.application

import com.loopers.account.domain.Account
import com.loopers.account.domain.AccountRepository
import com.loopers.account.domain.error.AccountErrorCode
import com.loopers.account.domain.vo.AccountName
import com.loopers.account.domain.vo.Email
import com.loopers.support.error.NotFoundException
import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AccountMeServiceTest {
    private val accountRepository: AccountRepository = mock()
    private val service = AccountMeService(accountRepository)

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

    private fun createAccount(): Account =
        Account(
            name = AccountName(ACCOUNT_NAME),
            birthDate = BIRTH_DATE,
            email = Email(EMAIL),
        )

    private companion object {
        private const val ACCOUNT_ID = 1L
        private const val LOGIN_ID = "shoeone96"
        private const val ACCOUNT_NAME = "홍길동"
        private const val MASKED_ACCOUNT_NAME = "홍길*"
        private const val EMAIL = "user@example.com"
        private val BIRTH_DATE: LocalDate = LocalDate.of(1996, 1, 1)
    }
}
