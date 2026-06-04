package com.loopers.domain.user.presentation.auth

import com.loopers.domain.user.application.UserFacade
import com.loopers.domain.user.application.UserInfo
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

@Component
class LoginUserArgumentResolver(
    private val userFacade: UserFacade,
) : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(LoginUser::class.java) &&
            parameter.parameterType == UserInfo::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Any {
        val request = webRequest.getNativeRequest(HttpServletRequest::class.java) ?: throwUnauthorized()
        val loginId = request.getHeader(CurrentUserInfoRequestHeaders.LOGIN_ID).requireNotBlank()
        val rawPassword = request.getHeader(CurrentUserInfoRequestHeaders.LOGIN_PW).requireNotBlank()
        return userFacade.getMe(loginId, rawPassword)
    }

    private fun String?.requireNotBlank(): String {
        if (this.isNullOrBlank()) {
            throwUnauthorized()
        }
        return this
    }

    private fun throwUnauthorized(): Nothing {
        throw CoreException(ErrorType.UNAUTHORIZED)
    }
}
