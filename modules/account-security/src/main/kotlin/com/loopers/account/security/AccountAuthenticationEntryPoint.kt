package com.loopers.account.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.interfaces.api.ApiResponseWriter
import com.loopers.support.error.CoreException
import com.loopers.support.error.UnauthorizedException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component

@Component
class AccountAuthenticationEntryPoint(
    objectMapper: ObjectMapper,
) : AuthenticationEntryPoint {
    private val apiResponseWriter = ApiResponseWriter(objectMapper)

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        apiResponseWriter.write(
            response = response,
            exception = resolveException(authException),
            status = HttpStatus.UNAUTHORIZED,
        )
    }

    private fun resolveException(authException: AuthenticationException): CoreException =
        when (authException) {
            is AccountAuthenticationException -> authException.coreException
            else -> UnauthorizedException()
        }
}
