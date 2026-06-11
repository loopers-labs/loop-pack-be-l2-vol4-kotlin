package com.loopers.account.domain

import com.loopers.account.domain.vo.AccountName
import com.loopers.account.domain.vo.CredentialIdentifier
import com.loopers.account.domain.vo.CredentialSecret
import com.loopers.account.domain.vo.Email
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.time.LocalDate

class AccountCredentialTest {
    @DisplayName("PASSWORD 인증 정보 값이 주어지면, account credential을 생성한다.")
    @Test
    fun createsAccountCredential_whenPasswordCredentialValuesAreProvided() {
        // given
        val account = createAccount()
        val method = CredentialMethod.PASSWORD
        val identifier = CredentialIdentifier(method, "shoeone96")
        val secret = CredentialSecret("{bcrypt}encrypted-password")

        // when
        val credential = AccountCredential(
            account = account,
            method = method,
            identifier = identifier,
            secret = secret,
        )

        // then
        assertAll(
            { assertThat(credential.account).isEqualTo(account) },
            { assertThat(credential.method).isEqualTo(method) },
            { assertThat(credential.identifier).isEqualTo(identifier) },
            { assertThat(credential.secret).isEqualTo(secret) },
        )
    }

    @DisplayName("credential secret을 새 값으로 교체한다.")
    @Test
    fun changesCredentialSecret() {
        // given
        val credential = AccountCredential(
            account = createAccount(),
            method = CredentialMethod.PASSWORD,
            identifier = CredentialIdentifier(CredentialMethod.PASSWORD, "shoeone96"),
            secret = CredentialSecret("{bcrypt}old-password"),
        )
        val newSecret = CredentialSecret("{bcrypt}new-password")

        // when
        credential.changeSecret(newSecret)

        // then
        assertThat(credential.secret).isEqualTo(newSecret)
    }

    private fun createAccount(): Account =
        Account(
            name = AccountName("홍길동"),
            birthDate = LocalDate.of(1996, 1, 1),
            email = Email("user@example.com"),
        )
}
