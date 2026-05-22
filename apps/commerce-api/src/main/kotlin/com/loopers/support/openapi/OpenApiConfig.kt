package com.loopers.support.openapi

import com.loopers.support.auth.AuthenticationInterceptor
import com.loopers.support.auth.CurrentUser
import com.loopers.support.auth.LoginRequired
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.customizers.OperationCustomizer
import org.springdoc.core.utils.SpringDocUtils
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.HandlerMethod

@Configuration
class OpenApiConfig {
    init {
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUser::class.java)
    }

    @Bean
    fun openApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("Loopers Commerce API")
                    .version("v1"),
            )
            .components(
                Components()
                    .addSecuritySchemes(LOGIN_ID_SCHEME, headerApiKey(AuthenticationInterceptor.LOGIN_ID_HEADER))
                    .addSecuritySchemes(LOGIN_PW_SCHEME, headerApiKey(AuthenticationInterceptor.LOGIN_PW_HEADER)),
            )

    @Bean
    fun loginRequiredSecurityCustomizer(): OperationCustomizer =
        OperationCustomizer { operation, handlerMethod ->
            if (handlerMethod.requiresLogin()) {
                operation.addSecurityItem(
                    SecurityRequirement()
                        .addList(LOGIN_ID_SCHEME)
                        .addList(LOGIN_PW_SCHEME),
                )
            }
            operation
        }

    private fun headerApiKey(headerName: String): SecurityScheme =
        SecurityScheme()
            .type(SecurityScheme.Type.APIKEY)
            .`in`(SecurityScheme.In.HEADER)
            .name(headerName)

    private fun HandlerMethod.requiresLogin(): Boolean =
        method.isAnnotationPresent(LoginRequired::class.java) ||
            beanType.isAnnotationPresent(LoginRequired::class.java)

    companion object {
        private const val LOGIN_ID_SCHEME = "LoginId"
        private const val LOGIN_PW_SCHEME = "LoginPw"
    }
}
