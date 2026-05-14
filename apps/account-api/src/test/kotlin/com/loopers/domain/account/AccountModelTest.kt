package com.loopers.domain.account

import com.loopers.domain.account.vo.AccountName
import com.loopers.domain.account.vo.BirthDate
import com.loopers.domain.account.vo.Email
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.time.LocalDate

class AccountModelTest {
    @DisplayName("계정 프로필 값이 주어지면, account를 생성한다.")
    @Test
    fun createsAccountModel_whenProfileValuesAreProvided() {
        // arrange
        val name = AccountName("홍길동")
        val birthDate = BirthDate(LocalDate.of(1996, 1, 1))
        val email = Email("user@example.com")

        // act
        val account = AccountModel(
            name = name,
            birthDate = birthDate,
            email = email,
        )

        // assert
        assertAll(
            { assertThat(account.name).isEqualTo(name) },
            { assertThat(account.birthDate).isEqualTo(birthDate) },
            { assertThat(account.email).isEqualTo(email) },
        )
    }

    @DisplayName("account의 마스킹 이름을 반환한다.")
    @Test
    fun returnsMaskedName() {
        // arrange
        val account = AccountModel(
            name = AccountName("홍길동"),
            birthDate = BirthDate(LocalDate.of(1996, 1, 1)),
            email = Email("user@example.com"),
        )

        // act
        val result = account.maskedName()

        // assert
        assertThat(result).isEqualTo("홍길*")
    }
}
