package com.loopers.support.auth

import com.loopers.domain.user.FakePasswordEncoder
import com.loopers.domain.user.FakeUserRepository
import com.loopers.domain.user.RawPassword
import com.loopers.domain.user.UserCommand
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.servlet.http.HttpServletResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.method.HandlerMethod
import java.time.LocalDate

class AuthenticationInterceptorTest {
    private val userRepository = FakeUserRepository()
    private val passwordEncoder = FakePasswordEncoder()
    private val userService = UserService(userRepository, passwordEncoder)
    private val interceptor = AuthenticationInterceptor(userService)

    private fun req(): MockHttpServletRequest = MockHttpServletRequest()
    private fun resp(): HttpServletResponse = MockHttpServletResponse()

    private fun handlerMethodFor(methodName: String): HandlerMethod {
        val method = TestController::class.java.getDeclaredMethod(methodName)
        return HandlerMethod(TestController(), method)
    }

    @Suppress("unused")
    class TestController {
        @LoginRequired
        fun protectedAction() = Unit

        fun publicAction() = Unit
    }

    @DisplayName("handler 가 HandlerMethod 가 아니면, true 를 반환하고 통과시킨다.")
    @Test
    fun returnsTrueWhenHandlerIsNotHandlerMethod() {
        val result = interceptor.preHandle(req(), resp(), "not-a-handler-method")

        assertThat(result).isTrue()
    }

    @DisplayName("@LoginRequired 가 없으면, 헤더 검사 없이 true 를 반환한다.")
    @Test
    fun returnsTrueWhenLoginRequiredAbsent() {
        val request = req() // no headers set
        val result = interceptor.preHandle(request, resp(), handlerMethodFor("publicAction"))

        assertThat(result).isTrue()
    }

    @DisplayName("@LoginRequired 가 있는데 헤더가 비어있으면, UNAUTHORIZED 예외를 던진다.")
    @Test
    fun throwsUnauthorizedWhenHeadersMissingOnProtectedEndpoint() {
        val ex = assertThrows<CoreException> {
            interceptor.preHandle(req(), resp(), handlerMethodFor("protectedAction"))
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
    }

    @DisplayName("@LoginRequired 가 있고 헤더가 올바르면, true 를 반환하고 요청 스코프에 User 를 저장한다.")
    @Test
    fun authenticatesAndStashesUserOnValidHeaders() {
        userService.register(
            UserCommand.Register(
                loginId = "loopers01",
                rawPassword = RawPassword("abcd1234"),
                name = "홍길동",
                birthdate = LocalDate.of(1990, 1, 1),
                email = "user@example.com",
            ),
        )
        val request = req().apply {
            addHeader("X-Loopers-LoginId", "loopers01")
            addHeader("X-Loopers-LoginPw", "abcd1234")
        }

        val result = interceptor.preHandle(request, resp(), handlerMethodFor("protectedAction"))

        assertThat(result).isTrue()
        val stashed = request.getAttribute(AuthenticationInterceptor.CURRENT_USER_KEY) as? com.loopers.domain.user.User
        assertThat(stashed).isNotNull()
        assertThat(stashed!!.loginId).isEqualTo("loopers01")
    }
}
