package com.loopers.account.security

import com.loopers.account.application.AccountAuthenticateCommand
import com.loopers.account.application.AccountService
import com.loopers.support.error.UnauthorizedException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.web.filter.OncePerRequestFilter

class AccountHeaderAuthenticationFilter(
    private val accountService: AccountService,
    private val authenticationEntryPoint: AuthenticationEntryPoint,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = requestPath(request)
        return (request.method == HttpMethod.POST.name() && path == USERS_PATH) ||
            path.startsWith(ACTUATOR_PATH_PREFIX) ||
            path.startsWith(SWAGGER_UI_PATH_PREFIX) ||
            path.startsWith(API_DOCS_PATH_PREFIX) ||
            path.startsWith(ADMIN_PATH_PREFIX)
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val loginId = request.getHeader(AccountAuthenticationHeaders.LOGIN_ID)
        val password = request.getHeader(AccountAuthenticationHeaders.PASSWORD)

        if (loginId == null && password == null) {
            filterChain.doFilter(request, response)
            return
        }

        if (loginId.isNullOrBlank() || password.isNullOrBlank()) {
            commenceUnauthorized(request, response, UnauthorizedException())
            return
        }

        runCatching {
            accountService.authenticate(
                AccountAuthenticateCommand(
                    loginId = loginId,
                    password = password,
                ),
            )
        }.onSuccess { authenticatedAccount ->
            val principal = AccountPrincipal(
                accountId = authenticatedAccount.accountId,
                loginId = authenticatedAccount.loginId,
            )
            request.setAttribute(AccountAuthenticationAttributes.ACCOUNT_ID, principal.accountId)
            request.setAttribute(AccountAuthenticationAttributes.LOGIN_ID, principal.loginId)
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
                principal,
                null,
                emptyList(),
            )
            filterChain.doFilter(request, response)
        }.onFailure { exception ->
            if (exception is UnauthorizedException) {
                commenceUnauthorized(request, response, exception)
            } else {
                throw exception
            }
        }
    }

    private fun commenceUnauthorized(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: UnauthorizedException,
    ) {
        SecurityContextHolder.clearContext()
        authenticationEntryPoint.commence(
            request,
            response,
            AccountAuthenticationException(exception),
        )
    }

    private fun requestPath(request: HttpServletRequest): String =
        request.servletPath.ifBlank { request.requestURI }

    private companion object {
        private const val USERS_PATH = "/api/v1/users"
        private const val ACTUATOR_PATH_PREFIX = "/actuator"
        private const val SWAGGER_UI_PATH_PREFIX = "/swagger-ui"
        private const val API_DOCS_PATH_PREFIX = "/v3/api-docs"
        private const val ADMIN_PATH_PREFIX = "/api-admin/v1/"
    }
}

object AccountAuthenticationHeaders {
    const val LOGIN_ID = "X-Loopers-LoginId"
    const val PASSWORD = "X-Loopers-LoginPw"
}

object AccountAuthenticationAttributes {
    const val ACCOUNT_ID = "accountId"
    const val LOGIN_ID = "loginId"
}

data class AccountPrincipal(
    val accountId: Long,
    val loginId: String,
)
