package com.loopers.account.persistence

import com.loopers.account.domain.Account
import com.loopers.account.domain.AccountCredential
import com.loopers.account.domain.CredentialMethod
import com.loopers.account.domain.vo.AccountName
import com.loopers.account.domain.vo.CredentialIdentifier
import com.loopers.account.domain.vo.CredentialSecret
import com.loopers.account.domain.vo.Email
import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import

@DataJpaTest
@EntityScan(basePackageClasses = [Account::class])
@Import(AccountPersistenceConfig::class)
class AccountDataRepositoryTest @Autowired constructor(
    private val accountJpaRepository: AccountJpaRepository,
    private val accountCredentialJpaRepository: AccountCredentialJpaRepository,
) {
    @DisplayName("credential을 저장하면 method와 identifier로 존재 여부를 조회한다.")
    @Test
    fun returnsExists_whenCredentialIsSaved() {
        // given
        val method = CredentialMethod.PASSWORD
        val identifier = CredentialIdentifier(method, "shoeone96")
        val account = accountJpaRepository.save(
            Account(
                name = AccountName("홍길동"),
                birthDate = LocalDate.of(1996, 1, 1),
                email = Email("user@example.com"),
            ),
        )
        accountCredentialJpaRepository.save(
            AccountCredential(
                account = account,
                method = method,
                identifier = identifier,
                secret = CredentialSecret("encoded-password"),
            ),
        )

        // when
        val exists = accountCredentialJpaRepository.existsByMethodAndIdentifierValue(method, identifier.value)
        val missing = accountCredentialJpaRepository.existsByMethodAndIdentifierValue(method, "other96")

        // then
        assertAll(
            { assertThat(exists).isTrue() },
            { assertThat(missing).isFalse() },
        )
    }
}

@SpringBootApplication
private class AccountPersistenceTestApplication
