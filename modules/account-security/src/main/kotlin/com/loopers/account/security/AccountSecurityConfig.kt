package com.loopers.account.security

import com.loopers.account.application.AccountAuthenticationService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
class AccountSecurityConfig(
    private val accountAuthenticationService: AccountAuthenticationService,
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
                    .requestMatchers(HttpMethod.POST, ACCOUNTS_PATH).permitAll()
                    .requestMatchers(ACTUATOR_PATH, SWAGGER_UI_PATH, API_DOCS_PATH).permitAll()
                    .anyRequest().authenticated()
            }
            .addFilterBefore(
                AccountHeaderAuthenticationFilter(accountAuthenticationService, accountAuthenticationEntryPoint),
                UsernamePasswordAuthenticationFilter::class.java,
            )
            .build()

    private companion object {
        private const val ACCOUNTS_PATH = "/accounts"
        private const val ACTUATOR_PATH = "/actuator/**"
        private const val SWAGGER_UI_PATH = "/swagger-ui/**"
        private const val API_DOCS_PATH = "/v3/api-docs/**"
    }
}
