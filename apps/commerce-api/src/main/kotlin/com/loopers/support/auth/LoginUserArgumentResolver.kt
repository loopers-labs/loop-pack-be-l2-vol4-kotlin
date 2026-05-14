package com.loopers.support.auth

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

@Component
class LoginUserArgumentResolver : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean {
        return parameter.hasParameterAnnotation(LoginAuth::class.java) &&
            parameter.parameterType == LoginUser::class.java
    }

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Any {
        val loginId = webRequest.getHeader(LOGIN_ID_HEADER)
        val rawPassword = webRequest.getHeader(LOGIN_PASSWORD_HEADER)

        if (loginId.isNullOrBlank() || rawPassword.isNullOrBlank()) {
            throw CoreException(ErrorType.UNAUTHORIZED)
        }

        return LoginUser(loginId = loginId, rawPassword = rawPassword)
    }

    companion object {
        private const val LOGIN_ID_HEADER = "X-Loopers-LoginId"
        private const val LOGIN_PASSWORD_HEADER = "X-Loopers-LoginPw"
    }
}
