package com.loopers.domain.account.vo

import com.loopers.support.error.AccountErrorCode
import com.loopers.support.error.BadRequestException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class BirthDateTest {
    @DisplayName("오늘 또는 과거 날짜가 주어지면, 생년월일 VO를 생성한다.")
    @Test
    fun createsBirthDate_whenDateIsTodayOrPast() {
        // arrange
        val value = LocalDate.of(1996, 1, 1)

        // act
        val birthDate = BirthDate(value)

        // assert
        assertThat(birthDate.value).isEqualTo(value)
    }

    @DisplayName("미래 날짜가 주어지면, BAD_REQUEST 예외가 발생한다.")
    @Test
    fun throwsBadRequestException_whenDateIsFuture() {
        // arrange
        val value = LocalDate.now().plusDays(1)

        // act
        val result = assertThrows<BadRequestException> {
            BirthDate(value)
        }

        // assert
        assertAll(
            { assertThat(result.errorCode).isEqualTo(AccountErrorCode.INVALID_BIRTH_DATE) },
        )
    }
}
