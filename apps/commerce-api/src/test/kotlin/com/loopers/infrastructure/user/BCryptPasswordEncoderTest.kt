package com.loopers.infrastructure.user

import com.loopers.domain.user.RawPassword
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class BCryptPasswordEncoderTest {
    private val encoder = BCryptPasswordEncoder()

    @DisplayName("encode 가 만든 해시는 동일한 RawPassword 와 matches=true 가 된다.")
    @Test
    fun encodedHashMatchesSameRawPassword() {
        val raw = RawPassword("abcd1234")

        val encoded = encoder.encode(raw)

        assertThat(encoder.matches(raw, encoded)).isTrue()
    }

    @DisplayName("encode 가 만든 해시는 다른 RawPassword 와 matches=false 가 된다.")
    @Test
    fun encodedHashDoesNotMatchDifferentRawPassword() {
        val encoded = encoder.encode(RawPassword("abcd1234"))

        assertThat(encoder.matches(RawPassword("wxyz5678"), encoded)).isFalse()
    }
}
