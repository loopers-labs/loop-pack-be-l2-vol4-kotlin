package com.loopers.account.infrastructure.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

class BCryptPasswordEncryptorTest {
    private val passwordEncryptor = BCryptPasswordEncryptor(BCryptPasswordEncoder())

    @DisplayName("암호화 전 비밀번호와 저장된 BCrypt hash가 일치하면 true를 반환한다.")
    @Test
    fun returnsTrue_whenRawPasswordMatchesEncodedPassword() {
        // given
        val encodedPassword = passwordEncryptor.encode(RAW_PASSWORD)

        // when
        val result = passwordEncryptor.matches(RAW_PASSWORD, encodedPassword)

        // then
        assertThat(result).isTrue()
    }

    @DisplayName("암호화 전 비밀번호와 저장된 BCrypt hash가 일치하지 않으면 false를 반환한다.")
    @Test
    fun returnsFalse_whenRawPasswordDoesNotMatchEncodedPassword() {
        // given
        val encodedPassword = passwordEncryptor.encode(RAW_PASSWORD)

        // when
        val result = passwordEncryptor.matches(WRONG_PASSWORD, encodedPassword)

        // then
        assertThat(result).isFalse()
    }

    private companion object {
        private const val RAW_PASSWORD = "abf15!@#"
        private const val WRONG_PASSWORD = "wrong15!@#"
    }
}
