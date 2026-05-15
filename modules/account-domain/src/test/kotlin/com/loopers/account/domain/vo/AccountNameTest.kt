package com.loopers.account.domain.vo

import com.loopers.account.domain.error.AccountErrorCode
import com.loopers.support.error.BadRequestException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

class AccountNameTest {
    @DisplayName("정상 이름이 주어지면, 이름 VO를 생성한다.")
    @Test
    fun createsAccountName_whenValueIsNotBlank() {
        // given
        val value = "홍길동"

        // when
        val accountName = AccountName(value)

        // then
        assertThat(accountName.value).isEqualTo(value)
    }

    @DisplayName("이름이 빈칸으로만 이루어져 있으면, BAD_REQUEST 예외가 발생한다.")
    @Test
    fun throwsBadRequestException_whenValueIsBlank() {
        // given
        val value = "   "

        // when
        val result = assertThrows<BadRequestException> {
            AccountName(value)
        }

        // then
        assertAll(
            { assertThat(result.errorCode).isEqualTo(AccountErrorCode.INVALID_ACCOUNT_NAME) },
        )
    }

    @DisplayName("한 글자 이름은 * 로 마스킹한다.")
    @Test
    fun masksSingleCharacterName() {
        // given
        val accountName = AccountName("김")

        // when
        val result = accountName.masked()

        // then
        assertThat(result).isEqualTo("*")
    }

    @DisplayName("두 글자 이상 이름은 마지막 글자만 * 로 마스킹한다.")
    @Test
    fun masksLastCharacterOnly_whenNameHasMultipleCharacters() {
        // given
        val accountName = AccountName("홍길동")

        // when
        val result = accountName.masked()

        // then
        assertThat(result).isEqualTo("홍길*")
    }
}
