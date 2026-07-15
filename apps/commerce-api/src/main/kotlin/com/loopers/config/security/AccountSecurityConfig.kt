package com.loopers.config.security

import com.loopers.account.application.AccountService
import com.loopers.account.infrastructure.security.AccountAuthenticationEntryPoint
import com.loopers.account.infrastructure.security.AccountHeaderAuthenticationFilter
import com.loopers.account.infrastructure.security.AdminLdapAuthenticationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
class AccountSecurityConfig(
    private val accountService: AccountService,
    private val accountAuthenticationEntryPoint: AccountAuthenticationEntryPoint,
) {
    @Bean
    fun accountSecurityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling { it.authenticationEntryPoint(accountAuthenticationEntryPoint) }
            .authorizeHttpRequests {
                it
                    .requestMatchers(HttpMethod.POST, USERS_PATH).permitAll()
                    .requestMatchers(HttpMethod.GET, PRODUCTS_PATH, PRODUCTS_DETAIL_PATH, RANKINGS_PATH).permitAll()
                    .requestMatchers(HttpMethod.POST, PAYMENT_CALLBACK_PATH).permitAll()
                    .requestMatchers(ACTUATOR_PATH, SWAGGER_UI_PATH, API_DOCS_PATH).permitAll()
                    .requestMatchers(ADMIN_PATH).authenticated()
                    .anyRequest().authenticated()
            }
            .addFilterBefore(
                AdminLdapAuthenticationFilter(accountService, accountAuthenticationEntryPoint),
                UsernamePasswordAuthenticationFilter::class.java,
            )
            .addFilterBefore(
                AccountHeaderAuthenticationFilter(accountService, accountAuthenticationEntryPoint),
                UsernamePasswordAuthenticationFilter::class.java,
            )
            .build()

    private companion object {
        private const val USERS_PATH = "/api/v1/users"
        private const val PRODUCTS_PATH = "/api/v1/products"
        private const val PRODUCTS_DETAIL_PATH = "/api/v1/products/*"
        private const val RANKINGS_PATH = "/api/v1/rankings"
        private const val PAYMENT_CALLBACK_PATH = "/api/v1/payments/callback"
        private const val ACTUATOR_PATH = "/actuator/**"
        private const val SWAGGER_UI_PATH = "/swagger-ui/**"
        private const val API_DOCS_PATH = "/v3/api-docs/**"
        private const val ADMIN_PATH = "/api-admin/v1/**"
    }
}
