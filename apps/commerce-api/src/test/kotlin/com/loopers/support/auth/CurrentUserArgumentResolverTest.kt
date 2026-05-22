package com.loopers.support.auth

import com.loopers.domain.user.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.core.MethodParameter
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.method.support.ModelAndViewContainer
import java.time.LocalDate

class CurrentUserArgumentResolverTest {
    private val resolver = CurrentUserArgumentResolver()

    @Suppress("unused")
    class Target {
        fun handle(@CurrentUser user: User) = Unit
    }

    private val parameter: MethodParameter = MethodParameter(Target::class.java.getDeclaredMethod("handle", User::class.java), 0)

    private fun webRequest(request: MockHttpServletRequest): NativeWebRequest = ServletWebRequest(request)

    @DisplayName("request 스코프에 User 가 있으면, 그대로 반환한다.")
    @Test
    fun returnsUserFromRequestAttribute() {
        val user = User(
            loginId = "loopers01",
            encryptedPassword = "hashed",
            name = "홍길동",
            birthdate = LocalDate.of(1990, 1, 1),
            email = "user@example.com",
        )
        val request = MockHttpServletRequest().apply {
            setAttribute(AuthenticationInterceptor.CURRENT_USER_KEY, user)
        }

        val resolved = resolver.resolveArgument(
            parameter,
            ModelAndViewContainer(),
            webRequest(request),
            null as WebDataBinderFactory?,
        )

        assertThat(resolved).isSameAs(user)
    }

    @DisplayName("request 스코프에 User 가 없으면, IllegalStateException 을 던진다.")
    @Test
    fun throwsIllegalStateWhenNoUserStashed() {
        val request = MockHttpServletRequest()

        assertThrows<IllegalStateException> {
            resolver.resolveArgument(
                parameter,
                ModelAndViewContainer(),
                webRequest(request),
                null as WebDataBinderFactory?,
            )
        }
    }
}
