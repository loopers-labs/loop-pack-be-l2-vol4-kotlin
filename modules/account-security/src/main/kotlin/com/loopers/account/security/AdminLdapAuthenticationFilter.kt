package com.loopers.account.security

import com.loopers.account.application.AccountAuthenticateCommand
import com.loopers.account.application.AccountService
import com.loopers.account.domain.AccountRole
import com.loopers.support.error.UnauthorizedException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.web.filter.OncePerRequestFilter

class AdminLdapAuthenticationFilter(
    private val accountService: AccountService,
    private val authenticationEntryPoint: AuthenticationEntryPoint,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !requestPath(request).startsWith(ADMIN_PATH_PREFIX)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val ldap = request.getHeader(AdminAuthenticationHeaders.LDAP)
        if (ldap != AdminAuthenticationHeaders.ADMIN_VALUE) {
            commenceUnauthorized(request, response, UnauthorizedException())
            return
        }

        val loginId = request.getHeader(AccountAuthenticationHeaders.LOGIN_ID)
        val password = request.getHeader(AccountAuthenticationHeaders.PASSWORD)
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
        }.onSuccess { info ->
            if (info.role != AccountRole.ADMIN) {
                commenceUnauthorized(request, response, UnauthorizedException())
                return
            }
            val principal = AdminPrincipal(
                accountId = info.accountId,
                loginId = info.loginId,
            )
            request.setAttribute(AccountAuthenticationAttributes.ACCOUNT_ID, principal.accountId)
            request.setAttribute(AccountAuthenticationAttributes.LOGIN_ID, principal.loginId)
            request.setAttribute(AdminAuthenticationAttributes.ADMIN, true)
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
                principal,
                null,
                listOf(SimpleGrantedAuthority(ROLE_ADMIN_AUTHORITY)),
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
        private const val ADMIN_PATH_PREFIX = "/api-admin/v1/"
        private const val ROLE_ADMIN_AUTHORITY = "ROLE_ADMIN"
    }
}

object AdminAuthenticationHeaders {
    const val LDAP = "X-Loopers-Ldap"
    const val ADMIN_VALUE = "loopers.admin"
}

object AdminAuthenticationAttributes {
    const val ADMIN = "admin"
}

data class AdminPrincipal(
    val accountId: Long,
    val loginId: String,
)
