package com.loopers.interfaces.api.queue

import com.loopers.application.queue.QueueFacade
import com.loopers.application.queue.QueueProperties
import com.loopers.domain.queue.QueueErrorType
import com.loopers.interfaces.api.ApiControllerAdvice
import com.loopers.interfaces.api.auth.AuthInterceptor
import com.loopers.interfaces.api.auth.AuthUser
import com.loopers.support.error.CoreException
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

class EntryTokenInterceptorTest {
    private val queueFacade: QueueFacade = mockk()

    private fun mockMvc(gateEnabled: Boolean): MockMvc =
        MockMvcBuilders.standaloneSetup(StubController())
            .addInterceptors(EntryTokenInterceptor(queueFacade, QueueProperties(gateEnabled = gateEnabled)))
            .setControllerAdvice(ApiControllerAdvice())
            .build()

    @RestController
    class StubController {
        @PostMapping("/test/gated")
        @RequireEntryToken
        fun gated(): String = "ok"
    }

    @DisplayName("게이트가 켜져 있으면, 토큰 없는 요청을 ENTRY_TOKEN_REQUIRED 로 거절한다.")
    @Test
    fun rejectsWithoutToken_whenGateEnabled() {
        mockMvc(gateEnabled = true)
            .perform(
                post("/test/gated")
                    .requestAttr(AuthInterceptor.ATTRIBUTE_AUTH_USER, AuthUser(id = 1L, loginId = "tester1")),
            )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.meta.errorCode").value("ENTRY_TOKEN_REQUIRED"))
    }

    @DisplayName("게이트가 꺼져 있으면, 토큰 없이도 검증 없이 통과한다.")
    @Test
    fun bypassesValidation_whenGateDisabled() {
        justRun { queueFacade.ensureAdmitted(any(), any()) }

        mockMvc(gateEnabled = false)
            .perform(
                post("/test/gated")
                    .requestAttr(AuthInterceptor.ATTRIBUTE_AUTH_USER, AuthUser(id = 1L, loginId = "tester1")),
            )
            .andExpect(status().isOk)

        verify(exactly = 0) { queueFacade.ensureAdmitted(any(), any()) }
    }

    @DisplayName("유효한 토큰이 있으면, 요청을 통과시킨다.")
    @Test
    fun passesWithValidToken_whenGateEnabled() {
        justRun { queueFacade.ensureAdmitted(any(), any()) }

        mockMvc(gateEnabled = true)
            .perform(
                post("/test/gated")
                    .header(EntryTokenInterceptor.HEADER_ENTRY_TOKEN, "valid-token")
                    .requestAttr(AuthInterceptor.ATTRIBUTE_AUTH_USER, AuthUser(id = 1L, loginId = "tester1")),
            )
            .andExpect(status().isOk)

        verify(exactly = 1) { queueFacade.ensureAdmitted(1L, "valid-token") }
    }

    @DisplayName("ensureAdmitted 가 예외를 던지면, 403 ENTRY_TOKEN_INVALID 로 거절한다.")
    @Test
    fun rejectsWithInvalidToken_whenGateEnabled() {
        every { queueFacade.ensureAdmitted(any(), any()) } throws CoreException(QueueErrorType.ENTRY_TOKEN_INVALID)

        mockMvc(gateEnabled = true)
            .perform(
                post("/test/gated")
                    .header(EntryTokenInterceptor.HEADER_ENTRY_TOKEN, "bogus-token")
                    .requestAttr(AuthInterceptor.ATTRIBUTE_AUTH_USER, AuthUser(id = 1L, loginId = "tester1")),
            )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.meta.errorCode").value("ENTRY_TOKEN_INVALID"))
    }
}
