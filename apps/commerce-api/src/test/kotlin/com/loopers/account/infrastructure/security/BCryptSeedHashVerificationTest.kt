package com.loopers.account.infrastructure.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

/**
 * data.sql에 박는 admin 비밀번호 BCrypt 해시가 Spring 검증과 호환되는지 sanity.
 * 해시 변경 시 본 테스트도 같이 갱신.
 */
class BCryptSeedHashVerificationTest {
    @DisplayName("data.sql에 박힌 admin BCrypt 해시는 Admin@Loop2026 평문과 매칭된다.")
    @Test
    fun verifiesAdminSeedHash() {
        val raw = "Admin@Loop2026"
        val hash = "\$2a\$10\$Sv5ZNc9oOeC9mLGu2ftbuO5A8UKM3q2JJPa/R.xwVx1fs67FAPHv2"
        assertThat(BCryptPasswordEncoder().matches(raw, hash)).isTrue()
    }
}
