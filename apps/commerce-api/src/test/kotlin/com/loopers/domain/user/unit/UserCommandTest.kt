package com.loopers.domain.user.unit

import com.loopers.domain.user.application.command.UserChangePasswordCommand
import com.loopers.domain.user.support.UserSteps.Companion.기본_생년월일
import com.loopers.domain.user.support.UserSteps.Companion.사용자_회원가입
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UserCommandTest {
    @Test
    fun `회원가입_커맨드는_민감정보를_toString에_노출하지_않는다`() {
        val command = 사용자_회원가입(
            rawPassword = "Password1!",
            email = "secret@example.com",
            birthday = 기본_생년월일,
        )

        val text = command.toString()

        assertThat(text).doesNotContain("Password1!")
        assertThat(text).doesNotContain("secret@example.com")
        assertThat(text).contains("rawPassword=<masked>")
        assertThat(text).contains("email=<masked>")
    }

    @Test
    fun `비밀번호_변경_커맨드는_민감정보를_toString에_노출하지_않는다`() {
        val command = UserChangePasswordCommand(
            userId = 1L,
            currentRawPassword = "Password1!",
            newRawPassword = "NewPass1!",
        )

        val text = command.toString()

        assertThat(text).doesNotContain("Password1!")
        assertThat(text).doesNotContain("NewPass1!")
        assertThat(text).contains("currentRawPassword=<masked>")
        assertThat(text).contains("newRawPassword=<masked>")
    }
}
