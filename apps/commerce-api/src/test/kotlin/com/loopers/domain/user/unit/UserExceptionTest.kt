package com.loopers.domain.user.unit

import com.loopers.domain.user.exception.DuplicateLoginIdException
import com.loopers.domain.user.exception.UserDomainException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UserExceptionTest {
    @Test
    fun `중복_로그인ID_예외는_사용자_도메인_예외다`() {
        val exception = DuplicateLoginIdException("sensitiveUser123")

        assertThat(exception).isInstanceOf(UserDomainException::class.java)
    }

    @Test
    fun `중복_로그인ID_예외_메시지는_원본_로그인ID를_노출하지_않는다`() {
        val loginId = "sensitiveUser123"

        val exception = DuplicateLoginIdException(loginId)

        assertThat(exception.message).doesNotContain(loginId)
    }
}
