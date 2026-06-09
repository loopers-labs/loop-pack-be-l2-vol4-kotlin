package com.loopers.account.security

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.loopers.account.application.AccountAuthenticateCommand
import com.loopers.account.application.AccountAuthenticatedInfo
import com.loopers.account.application.AccountService
import com.loopers.account.domain.AccountRole
import com.loopers.support.error.UnauthorizedException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder

class AdminLdapAuthenticationFilterTest {
    private val accountService: AccountService = mock()
    private val entryPoint = AccountAuthenticationEntryPoint(jacksonObjectMapper().findAndRegisterModules())
    private val filter = AdminLdapAuthenticationFilter(accountService, entryPoint)

    @BeforeEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    @DisplayName("admin 경로가 아니면 필터링하지 않고 chain을 통과시킨다.")
    @Test
    fun skipsFiltering_whenPathIsNotAdmin() {
        // given
        val request = MockHttpServletRequest("GET", "/api/v1/products")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        // when
        filter.doFilter(request, response, chain)

        // then
        assertAll(
            { assertThat(chain.request).isSameAs(request) },
            { assertThat(request.getAttribute(AdminAuthenticationAttributes.ADMIN)).isNull() },
            { assertThat(SecurityContextHolder.getContext().authentication).isNull() },
        )
    }

    @DisplayName("admin 경로인데 Ldap 헤더가 없으면 401을 반환한다.")
    @Test
    fun returnsUnauthorized_whenLdapHeaderIsMissing() {
        // given
        val request = adminRequest()
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        // when
        filter.doFilter(request, response, chain)

        // then
        assertUnauthorized(response, chain)
        verify(accountService, never()).authenticate(any())
    }

    @DisplayName("admin 경로인데 Ldap 헤더 값이 loopers.admin이 아니면 401을 반환한다.")
    @Test
    fun returnsUnauthorized_whenLdapHeaderValueIsWrong() {
        // given
        val request = adminRequest()
        request.addHeader(AdminAuthenticationHeaders.LDAP, "not-loopers-admin")
        request.addHeader(AccountAuthenticationHeaders.LOGIN_ID, LOGIN_ID)
        request.addHeader(AccountAuthenticationHeaders.PASSWORD, PASSWORD)
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        // when
        filter.doFilter(request, response, chain)

        // then
        assertUnauthorized(response, chain)
        verify(accountService, never()).authenticate(any())
    }

    @DisplayName("Ldap 헤더는 통과해도 LoginId/LoginPw 헤더가 없으면 401을 반환한다.")
    @Test
    fun returnsUnauthorized_whenLoginHeadersAreMissing() {
        // given
        val request = adminRequest()
        request.addHeader(AdminAuthenticationHeaders.LDAP, AdminAuthenticationHeaders.ADMIN_VALUE)
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        // when
        filter.doFilter(request, response, chain)

        // then
        assertUnauthorized(response, chain)
        verify(accountService, never()).authenticate(any())
    }

    @DisplayName("AccountService.authenticate가 UnauthorizedException을 던지면 401을 반환한다.")
    @Test
    fun returnsUnauthorized_whenAuthenticateFails() {
        // given
        val request = adminRequest()
        request.addHeader(AdminAuthenticationHeaders.LDAP, AdminAuthenticationHeaders.ADMIN_VALUE)
        request.addHeader(AccountAuthenticationHeaders.LOGIN_ID, LOGIN_ID)
        request.addHeader(AccountAuthenticationHeaders.PASSWORD, PASSWORD)
        whenever(accountService.authenticate(any())).thenThrow(UnauthorizedException())
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        // when
        filter.doFilter(request, response, chain)

        // then
        assertUnauthorized(response, chain)
    }

    @DisplayName("인증된 account의 role이 USER이면 401을 반환한다.")
    @Test
    fun returnsUnauthorized_whenAuthenticatedAccountIsNotAdmin() {
        // given
        val request = adminRequest()
        request.addHeader(AdminAuthenticationHeaders.LDAP, AdminAuthenticationHeaders.ADMIN_VALUE)
        request.addHeader(AccountAuthenticationHeaders.LOGIN_ID, LOGIN_ID)
        request.addHeader(AccountAuthenticationHeaders.PASSWORD, PASSWORD)
        whenever(accountService.authenticate(any())).thenReturn(
            AccountAuthenticatedInfo(accountId = ACCOUNT_ID, loginId = LOGIN_ID, role = AccountRole.USER),
        )
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        // when
        filter.doFilter(request, response, chain)

        // then
        assertUnauthorized(response, chain)
    }

    @DisplayName("Ldap + LoginId/LoginPw + role=ADMIN 네 조건이 모두 충족되면 chain을 통과시키고 SecurityContext에 AdminPrincipal을 설정한다.")
    @Test
    fun authenticatesAdmin_whenAllConditionsAreSatisfied() {
        // given
        val request = adminRequest()
        request.addHeader(AdminAuthenticationHeaders.LDAP, AdminAuthenticationHeaders.ADMIN_VALUE)
        request.addHeader(AccountAuthenticationHeaders.LOGIN_ID, LOGIN_ID)
        request.addHeader(AccountAuthenticationHeaders.PASSWORD, PASSWORD)
        whenever(accountService.authenticate(any())).thenReturn(
            AccountAuthenticatedInfo(accountId = ACCOUNT_ID, loginId = LOGIN_ID, role = AccountRole.ADMIN),
        )
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        // when
        filter.doFilter(request, response, chain)

        // then
        val captured = SecurityContextHolder.getContext().authentication
        assertAll(
            { assertThat(chain.request).isSameAs(request) },
            { assertThat(response.status).isEqualTo(HttpStatus.OK.value()) },
            { assertThat(request.getAttribute(AdminAuthenticationAttributes.ADMIN)).isEqualTo(true) },
            { assertThat(request.getAttribute(AccountAuthenticationAttributes.ACCOUNT_ID)).isEqualTo(ACCOUNT_ID) },
            { assertThat(request.getAttribute(AccountAuthenticationAttributes.LOGIN_ID)).isEqualTo(LOGIN_ID) },
            { assertThat(captured).isNotNull },
            { assertThat(captured.principal).isInstanceOf(AdminPrincipal::class.java) },
            { assertThat((captured.principal as AdminPrincipal).accountId).isEqualTo(ACCOUNT_ID) },
            { assertThat((captured.principal as AdminPrincipal).loginId).isEqualTo(LOGIN_ID) },
            { assertThat(captured.authorities.map { it.authority }).containsExactly("ROLE_ADMIN") },
            { verify(accountService).authenticate(AccountAuthenticateCommand(LOGIN_ID, PASSWORD)) },
        )
    }

    private fun adminRequest(): MockHttpServletRequest = MockHttpServletRequest("GET", "/api-admin/v1/brands")

    private fun assertUnauthorized(response: MockHttpServletResponse, chain: MockFilterChain) {
        assertAll(
            { assertThat(response.status).isEqualTo(HttpStatus.UNAUTHORIZED.value()) },
            { assertThat(chain.request).isNull() },
            { assertThat(SecurityContextHolder.getContext().authentication).isNull() },
        )
    }

    private companion object {
        private const val ACCOUNT_ID = 1L
        private const val LOGIN_ID = "loopers.admin"
        private const val PASSWORD = "Admin@Loop2026"
    }
}
