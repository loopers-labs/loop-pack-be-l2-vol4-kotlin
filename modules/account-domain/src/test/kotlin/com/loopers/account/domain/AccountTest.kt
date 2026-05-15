package com.loopers.account.domain

import com.loopers.account.domain.vo.AccountName
import com.loopers.account.domain.vo.Email
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.time.LocalDate

class AccountTest {
    @DisplayName("계정 프로필 값이 주어지면, account를 생성한다.")
    @Test
    fun createsAccount_whenProfileValuesAreProvided() {
        // given
        val name = AccountName("홍길동")
        val birthDate = LocalDate.of(1996, 1, 1)
        val email = Email("user@example.com")

        // when
        val account = Account(
            name = name,
            birthDate = birthDate,
            email = email,
        )

        // then
        assertAll(
            { assertThat(account.name).isEqualTo(name) },
            { assertThat(account.birthDate).isEqualTo(birthDate) },
            { assertThat(account.email).isEqualTo(email) },
        )
    }

    @DisplayName("account의 마스킹 이름을 반환한다.")
    @Test
    fun returnsMaskedName() {
        // given
        val account = Account(
            name = AccountName("홍길동"),
            birthDate = LocalDate.of(1996, 1, 1),
            email = Email("user@example.com"),
        )

        // when
        val result = account.maskedName()

        // then
        assertThat(result).isEqualTo("홍길*")
    }
}
