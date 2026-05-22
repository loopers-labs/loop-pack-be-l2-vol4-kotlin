package com.loopers.support.auth

import com.loopers.domain.user.User
import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

@Component
class CurrentUserArgumentResolver : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(CurrentUser::class.java) &&
            User::class.java.isAssignableFrom(parameter.parameterType)

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Any {
        val request = webRequest.getNativeRequest(HttpServletRequest::class.java)
            ?: error("HttpServletRequest 가 NativeWebRequest 에 없습니다.")
        return request.getAttribute(AuthenticationInterceptor.CURRENT_USER_KEY)
            ?: error("@CurrentUser 가 @LoginRequired 없는 핸들러에서 사용되었습니다.")
    }
}
