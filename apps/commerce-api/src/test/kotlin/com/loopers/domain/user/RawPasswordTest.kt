package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RawPasswordTest {
    @DisplayName("8자리 영숫자 비밀번호로 생성하면, value 에 동일한 값을 보관한다.")
    @Test
    fun accepts8CharAlphanumericPassword() {
        val raw = RawPassword("abcd1234")

        assertThat(raw.value).isEqualTo("abcd1234")
    }

    @DisplayName("7자리 비밀번호로 생성하면, BAD_REQUEST 예외를 던진다.")
    @Test
    fun rejects7CharPassword() {
        val ex = assertThrows<CoreException> { RawPassword("abc1234") }

        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }

    @DisplayName("17자리 비밀번호로 생성하면, BAD_REQUEST 예외를 던진다.")
    @Test
    fun rejects17CharPassword() {
        val ex = assertThrows<CoreException> { RawPassword("abcd1234abcd12345") }

        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }

    @DisplayName("허용되지 않는 문자(한글)가 포함되면, BAD_REQUEST 예외를 던진다.")
    @Test
    fun rejectsPasswordWithKoreanCharacter() {
        val ex = assertThrows<CoreException> { RawPassword("abcd1234한") }

        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }

    @DisplayName("toString 결과는 평문을 노출하지 않고 *** 를 반환한다.")
    @Test
    fun toStringRedactsValue() {
        val raw = RawPassword("abcd1234")

        assertThat(raw.toString()).isEqualTo("***")
    }
}
