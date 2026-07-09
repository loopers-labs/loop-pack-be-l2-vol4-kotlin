package com.loopers.config.web

import com.loopers.interfaces.api.auth.AuthArgumentResolver
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * 웹 계층 횡단 관심사(인증 어노테이션 등) 전역 등록.
 */
@Configuration
class WebMvcConfig(
    private val authArgumentResolver: AuthArgumentResolver,
) : WebMvcConfigurer {
    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(authArgumentResolver)
    }
}
