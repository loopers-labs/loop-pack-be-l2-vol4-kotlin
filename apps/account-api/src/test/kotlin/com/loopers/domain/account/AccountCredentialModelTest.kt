package com.loopers.domain.account

import com.loopers.domain.account.vo.AccountName
import com.loopers.domain.account.vo.BirthDate
import com.loopers.domain.account.vo.CredentialIdentifier
import com.loopers.domain.account.vo.CredentialSecret
import com.loopers.domain.account.vo.Email
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.time.LocalDate

class AccountCredentialModelTest {
    @DisplayName("PASSWORD 인증 정보 값이 주어지면, account credential을 생성한다.")
    @Test
    fun createsAccountCredentialModel_whenPasswordCredentialValuesAreProvided() {
        // arrange
        val account = createAccount()
        val method = CredentialMethod.PASSWORD
        val identifier = CredentialIdentifier(method, "shoeone96")
        val secret = CredentialSecret("{bcrypt}encrypted-password")

        // act
        val credential = AccountCredentialModel(
            account = account,
            method = method,
            identifier = identifier,
            secret = secret,
        )

        // assert
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
        // arrange
        val credential = AccountCredentialModel(
            account = createAccount(),
            method = CredentialMethod.PASSWORD,
            identifier = CredentialIdentifier(CredentialMethod.PASSWORD, "shoeone96"),
            secret = CredentialSecret("{bcrypt}old-password"),
        )
        val newSecret = CredentialSecret("{bcrypt}new-password")

        // act
        credential.changeSecret(newSecret)

        // assert
        assertThat(credential.secret).isEqualTo(newSecret)
    }

    private fun createAccount(): AccountModel =
        AccountModel(
            name = AccountName("홍길동"),
            birthDate = BirthDate(LocalDate.of(1996, 1, 1)),
            email = Email("user@example.com"),
        )
}
