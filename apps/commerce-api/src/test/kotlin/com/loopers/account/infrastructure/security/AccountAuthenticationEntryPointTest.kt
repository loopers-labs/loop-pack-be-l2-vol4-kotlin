package com.loopers.account.infrastructure.security

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.loopers.support.error.CommonErrorCode
import com.loopers.support.error.UnauthorizedException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.BadCredentialsException

class AccountAuthenticationEntryPointTest {
    private val objectMapper = jacksonObjectMapper().findAndRegisterModules()
    private val entryPoint = AccountAuthenticationEntryPoint(objectMapper)

    @DisplayName("account 인증 예외가 발생하면 동일한 ApiResponse 실패 형식으로 401 응답을 반환한다.")
    @Test
    fun writesUnauthorizedApiResponse_whenAccountAuthenticationExceptionOccurs() {
        // given
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        val exception = AccountAuthenticationException(UnauthorizedException())

        // when
        entryPoint.commence(request, response, exception)

        // then
        assertUnauthorizedApiResponse(response)
    }

    @DisplayName("Spring Security 인증 예외가 발생해도 동일한 ApiResponse 실패 형식으로 401 응답을 반환한다.")
    @Test
    fun writesUnauthorizedApiResponse_whenSpringAuthenticationExceptionOccurs() {
        // given
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        val exception = BadCredentialsException(BAD_CREDENTIALS_MESSAGE)

        // when
        entryPoint.commence(request, response, exception)

        // then
        assertUnauthorizedApiResponse(response)
    }

    private fun assertUnauthorizedApiResponse(response: MockHttpServletResponse) {
        val body = objectMapper.readTree(response.contentAsString)
        val contentType = requireNotNull(response.contentType)
        assertThat(response.status).isEqualTo(UNAUTHORIZED_STATUS.value())
        assertThat(MediaType.parseMediaType(contentType).isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue()
        assertThat(body["isSuccess"].asBoolean()).isFalse()
        assertThat(body["status"].asInt()).isEqualTo(UNAUTHORIZED_STATUS.value())
        assertThat(body["code"].asText()).isEqualTo(UNAUTHORIZED_CODE)
    }

    private companion object {
        private val UNAUTHORIZED_STATUS = HttpStatus.UNAUTHORIZED
        private val UNAUTHORIZED_CODE = CommonErrorCode.UNAUTHORIZED.code
        private const val BAD_CREDENTIALS_MESSAGE = "bad credentials"
    }
}
