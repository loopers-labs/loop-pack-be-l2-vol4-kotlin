package com.loopers.domain.account.vo

import com.loopers.support.error.AccountErrorCode
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
        // arrange
        val value = "홍길동"

        // act
        val accountName = AccountName(value)

        // assert
        assertThat(accountName.value).isEqualTo(value)
    }

    @DisplayName("이름이 빈칸으로만 이루어져 있으면, BAD_REQUEST 예외가 발생한다.")
    @Test
    fun throwsBadRequestException_whenValueIsBlank() {
        // arrange
        val value = "   "

        // act
        val result = assertThrows<BadRequestException> {
            AccountName(value)
        }

        // assert
        assertAll(
            { assertThat(result.errorCode).isEqualTo(AccountErrorCode.INVALID_ACCOUNT_NAME) },
        )
    }

    @DisplayName("한 글자 이름은 * 로 마스킹한다.")
    @Test
    fun masksSingleCharacterName() {
        // arrange
        val accountName = AccountName("김")

        // act
        val result = accountName.masked()

        // assert
        assertThat(result).isEqualTo("*")
    }

    @DisplayName("두 글자 이상 이름은 마지막 글자만 * 로 마스킹한다.")
    @Test
    fun masksLastCharacterOnly_whenNameHasMultipleCharacters() {
        // arrange
        val accountName = AccountName("홍길동")

        // act
        val result = accountName.masked()

        // assert
        assertThat(result).isEqualTo("홍길*")
    }
}
