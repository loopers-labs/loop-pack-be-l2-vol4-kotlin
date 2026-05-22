package com.loopers.support.auth

import com.loopers.domain.user.RawPassword
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

@Component
class AuthenticationInterceptor(
    private val userService: UserService,
) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (handler !is HandlerMethod) return true

        val requiresLogin =
            handler.method.isAnnotationPresent(LoginRequired::class.java) ||
                handler.beanType.isAnnotationPresent(LoginRequired::class.java)
        if (!requiresLogin) return true

        val loginId = request.getHeader(LOGIN_ID_HEADER)
        val loginPw = request.getHeader(LOGIN_PW_HEADER)
        if (loginId.isNullOrBlank() || loginPw.isNullOrBlank()) {
            throw CoreException(ErrorType.UNAUTHORIZED, "인증 헤더가 필요합니다.")
        }

        val user = userService.authenticate(loginId, RawPassword(loginPw))
        request.setAttribute(CURRENT_USER_KEY, user)
        return true
    }

    companion object {
        const val LOGIN_ID_HEADER = "X-Loopers-LoginId"
        const val LOGIN_PW_HEADER = "X-Loopers-LoginPw"
        const val CURRENT_USER_KEY = "com.loopers.support.auth.CURRENT_USER"
    }
}
