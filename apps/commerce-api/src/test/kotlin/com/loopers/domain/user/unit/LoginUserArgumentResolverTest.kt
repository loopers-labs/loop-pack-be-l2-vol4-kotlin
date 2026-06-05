package com.loopers.domain.user.unit

import com.loopers.domain.user.application.UserFacade
import com.loopers.domain.user.presentation.auth.CurrentUserInfoRequestHeaders
import com.loopers.domain.user.presentation.auth.LoginUserArgumentResolver
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.core.MethodParameter
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.ServletWebRequest

class LoginUserArgumentResolverTest {
    @Test
    fun `로그인ID_헤더가_blank면_facade_호출_없이_UNAUTHORIZED가_발생한다`() {
        val userFacade = mockk<UserFacade>()
        val resolver = LoginUserArgumentResolver(userFacade)
        every { userFacade.getMe(any(), any()) } throws AssertionError("facade must not be called")
        val request = authRequest(loginId = "   ")

        val ex = assertThrows<CoreException> {
            resolver.resolveArgument(mockk<MethodParameter>(), null, ServletWebRequest(request), null)
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        verify(exactly = 0) { userFacade.getMe(any(), any()) }
    }

    @Test
    fun `비밀번호_헤더가_blank면_facade_호출_없이_UNAUTHORIZED가_발생한다`() {
        val userFacade = mockk<UserFacade>()
        val resolver = LoginUserArgumentResolver(userFacade)
        every { userFacade.getMe(any(), any()) } throws AssertionError("facade must not be called")
        val request = authRequest(password = "   ")

        val ex = assertThrows<CoreException> {
            resolver.resolveArgument(mockk<MethodParameter>(), null, ServletWebRequest(request), null)
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        verify(exactly = 0) { userFacade.getMe(any(), any()) }
    }

    private fun authRequest(
        loginId: String = "user1234",
        password: String = "Password1!",
    ): MockHttpServletRequest =
        MockHttpServletRequest().apply {
            addHeader(CurrentUserInfoRequestHeaders.LOGIN_ID, loginId)
            addHeader(CurrentUserInfoRequestHeaders.LOGIN_PW, password)
        }
}
