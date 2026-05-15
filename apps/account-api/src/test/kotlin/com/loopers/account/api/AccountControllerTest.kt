package com.loopers.account.api

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class AccountControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
) {
    @DisplayName("POST /accounts")
    @Nested
    inner class Create {
        @DisplayName("유효한 회원가입 요청이면 성공 응답을 반환한다.")
        @Test
        fun returnsSuccess_whenValidRequestIsProvided() {
            mockMvc.performCreate(validCreateRequest())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.isSuccess").value(true))
        }

        @DisplayName("생년월일이 날짜 형식이 아니면 400 BAD_REQUEST 응답을 반환한다.")
        @Test
        fun returnsBadRequest_whenBirthDateIsInvalid() {
            val request = validCreateRequest() + ("birthDate" to "1996-13-40")

            mockMvc.performCreate(request)
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.isSuccess").value(false))
        }

        @DisplayName("비밀번호 형식이 유효하지 않으면 400 BAD_REQUEST 응답을 반환한다.")
        @Test
        fun returnsBadRequest_whenPasswordIsInvalid() {
            val request = validCreateRequest() + ("password" to "abc123!")

            mockMvc.performCreate(request)
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("ACCOUNT:INVALID_PASSWORD"))
        }

        @DisplayName("이메일 형식이 유효하지 않으면 400 BAD_REQUEST 응답을 반환한다.")
        @Test
        fun returnsBadRequest_whenEmailIsInvalid() {
            val request = validCreateRequest() + ("email" to "user@")

            mockMvc.performCreate(request)
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("ACCOUNT:INVALID_EMAIL"))
        }

        @DisplayName("로그인 ID 형식이 유효하지 않으면 400 BAD_REQUEST 응답을 반환한다.")
        @Test
        fun returnsBadRequest_whenLoginIdIsInvalid() {
            val request = validCreateRequest() + ("loginId" to "shoeone!")

            mockMvc.performCreate(request)
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("ACCOUNT:INVALID_CREDENTIAL_IDENTIFIER"))
        }
    }

    private fun MockMvc.performCreate(request: Map<String, String>) =
        perform(
            post("/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )

    private fun validCreateRequest(): Map<String, String> =
        mapOf(
            "loginId" to "shoeone96",
            "email" to "user@example.com",
            "password" to "abf15!@#",
            "name" to "홍길동",
            "birthDate" to "1996-01-01",
        )
}
