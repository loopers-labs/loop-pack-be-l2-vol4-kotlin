package com.loopers.domain.user

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PasswordEncoderTest {
    @DisplayName("패스워드 암호화")
    @Nested
    inner class Encode {
        @DisplayName("원문 패스워드를 다른 값으로 암호화한다")
        @Test
        fun encodesPasswordToDifferentValue() {
            val rawPassword = "Loopers123!"

            val encodedPassword = PasswordEncoder.encode(rawPassword)

            assertThat(encodedPassword).isNotEqualTo(rawPassword)
        }

        @DisplayName("동일한 원문 패스워드도 salt 로 인해 매번 다른 값으로 암호화한다")
        @Test
        fun encodesSamePasswordToDifferentValues() {
            val rawPassword = "Loopers123!"

            val firstEncodedPassword = PasswordEncoder.encode(rawPassword)
            val secondEncodedPassword = PasswordEncoder.encode(rawPassword)

            assertThat(firstEncodedPassword).isNotEqualTo(secondEncodedPassword)
        }
    }

    @DisplayName("패스워드 일치 여부 확인")
    @Nested
    inner class Match {
        @DisplayName("원문 패스워드가 암호화된 패스워드와 일치하면 true 를 반환한다")
        @Test
        fun returnsTrue_whenRawPasswordMatchesEncodedPassword() {
            val rawPassword = "Loopers123!"
            val encodedPassword = PasswordEncoder.encode(rawPassword)

            val isMatched = PasswordEncoder.matches(
                rawPassword = rawPassword,
                encodedPassword = encodedPassword,
            )

            assertThat(isMatched).isTrue()
        }

        @DisplayName("원문 패스워드가 암호화된 패스워드와 일치하지 않으면 false 를 반환한다")
        @Test
        fun returnsFalse_whenRawPasswordDoesNotMatchEncodedPassword() {
            val encodedPassword = PasswordEncoder.encode("Loopers123!")

            val isMatched = PasswordEncoder.matches(
                rawPassword = "Wrong123!",
                encodedPassword = encodedPassword,
            )

            assertThat(isMatched).isFalse()
        }
    }
}
