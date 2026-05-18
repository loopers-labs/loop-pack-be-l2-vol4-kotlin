package com.loopers.account.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.account.application.AccountCreateCommand
import com.loopers.account.application.AccountService
import com.loopers.support.error.CommonErrorCode
import java.time.LocalDate
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

@Transactional
@SpringBootTest
@AutoConfigureMockMvc
class AccountSecurityIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val accountService: AccountService,
) {
    @DisplayName("POST /api/v1/users")
    @Nested
    inner class Create {
        @DisplayName("인증 헤더 없이도 회원가입 요청은 허용된다.")
        @Test
        fun permitsCreateAccount_whenAuthenticationHeadersAreMissing() {
            mockMvc.performCreateAccount()
                .andExpect(status().isOk)
                .andExpect(jsonPath(IS_SUCCESS_PATH).value(true))
        }

        @DisplayName("잘못된 인증 헤더가 있어도 회원가입 요청은 허용된다.")
        @Test
        fun permitsCreateAccount_whenAuthenticationHeadersAreInvalid() {
            mockMvc.performCreateAccount(loginId = INVALID_LOGIN_ID, password = WRONG_PASSWORD)
                .andExpect(status().isOk)
                .andExpect(jsonPath(IS_SUCCESS_PATH).value(true))
        }
    }

    @DisplayName("GET /api/v1/users/me")
    @Nested
    inner class Me {
        @DisplayName("인증 헤더가 없으면 공통 실패 응답 형식으로 UNAUTHORIZED를 반환한다.")
        @Test
        fun returnsUnauthorized_whenAuthenticationHeadersAreMissing() {
            mockMvc.performGetMe()
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath(IS_SUCCESS_PATH).value(false))
                .andExpect(jsonPath(STATUS_PATH).value(UNAUTHORIZED_STATUS))
                .andExpect(jsonPath(CODE_PATH).value(UNAUTHORIZED_CODE))
        }

        @DisplayName("비밀번호가 일치하지 않으면 공통 실패 응답 형식으로 UNAUTHORIZED를 반환한다.")
        @Test
        fun returnsUnauthorized_whenPasswordDoesNotMatch() {
            createAccount()

            mockMvc.performGetMe(loginId = LOGIN_ID, password = WRONG_PASSWORD)
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath(IS_SUCCESS_PATH).value(false))
                .andExpect(jsonPath(STATUS_PATH).value(UNAUTHORIZED_STATUS))
                .andExpect(jsonPath(CODE_PATH).value(UNAUTHORIZED_CODE))
        }

        @DisplayName("올바른 로그인 ID와 비밀번호 헤더가 있으면 내 정보를 반환한다.")
        @Test
        fun returnsMyAccountInfo_whenAuthenticationHeadersAreValid() {
            createAccount()

            mockMvc.performGetMe(loginId = LOGIN_ID, password = RAW_PASSWORD)
                .andExpect(status().isOk)
                .andExpect(jsonPath(IS_SUCCESS_PATH).value(true))
                .andExpect(jsonPath(DATA_LOGIN_ID_PATH).value(LOGIN_ID))
                .andExpect(jsonPath(DATA_EMAIL_PATH).value(EMAIL))
        }
    }

    @DisplayName("PUT /api/v1/users/password")
    @Nested
    inner class ChangePassword {
        @DisplayName("인증 헤더가 없으면 공통 실패 응답 형식으로 UNAUTHORIZED를 반환한다.")
        @Test
        fun returnsUnauthorized_whenAuthenticationHeadersAreMissing() {
            mockMvc.performChangePassword()
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath(IS_SUCCESS_PATH).value(false))
                .andExpect(jsonPath(STATUS_PATH).value(UNAUTHORIZED_STATUS))
                .andExpect(jsonPath(CODE_PATH).value(UNAUTHORIZED_CODE))
        }

        @DisplayName("올바른 로그인 ID와 비밀번호 헤더가 있으면 비밀번호 수정 요청을 허용한다.")
        @Test
        fun permitsPasswordChange_whenAuthenticationHeadersAreValid() {
            createAccount()

            mockMvc.performChangePassword(loginId = LOGIN_ID, password = RAW_PASSWORD)
                .andExpect(status().isOk)
                .andExpect(jsonPath(IS_SUCCESS_PATH).value(true))
        }
    }

    private fun MockMvc.performCreateAccount(
        loginId: String? = null,
        password: String? = null,
    ) =
        perform(
            post(ACCOUNTS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .apply {
                    loginId?.let { header(LOGIN_ID_HEADER, it) }
                    password?.let { header(PASSWORD_HEADER, it) }
                }
                .content(objectMapper.writeValueAsString(createAccountRequest())),
        )

    private fun MockMvc.performGetMe(
        loginId: String? = null,
        password: String? = null,
    ) = perform(
        get(ACCOUNTS_ME_PATH)
            .apply {
                loginId?.let { header(LOGIN_ID_HEADER, it) }
                password?.let { header(PASSWORD_HEADER, it) }
            },
    )

    private fun MockMvc.performChangePassword(
        loginId: String? = null,
        password: String? = null,
    ) = perform(
        put(ACCOUNTS_PASSWORD_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .apply {
                loginId?.let { header(LOGIN_ID_HEADER, it) }
                password?.let { header(PASSWORD_HEADER, it) }
            }
            .content(objectMapper.writeValueAsString(passwordChangeRequest())),
    )

    private fun createAccount() {
        accountService.create(
            AccountCreateCommand(
                loginId = LOGIN_ID,
                email = EMAIL,
                password = RAW_PASSWORD,
                name = ACCOUNT_NAME,
                birthDate = BIRTH_DATE,
            ),
        )
    }

    private fun createAccountRequest(): Map<String, String> =
        mapOf(
            FIELD_LOGIN_ID to CREATE_LOGIN_ID,
            FIELD_EMAIL to CREATE_EMAIL,
            FIELD_PASSWORD to RAW_PASSWORD,
            FIELD_NAME to ACCOUNT_NAME,
            FIELD_BIRTH_DATE to BIRTH_DATE.toString(),
        )

    private fun passwordChangeRequest(): Map<String, String> =
        mapOf(
            FIELD_CURRENT_PASSWORD to RAW_PASSWORD,
            FIELD_NEW_PASSWORD to NEW_PASSWORD,
        )

    private companion object {
        private const val ACCOUNTS_PATH = "/api/v1/users"
        private const val ACCOUNTS_ME_PATH = "/api/v1/users/me"
        private const val ACCOUNTS_PASSWORD_PATH = "/api/v1/users/password"
        private const val LOGIN_ID_HEADER = AccountAuthenticationHeaders.LOGIN_ID
        private const val PASSWORD_HEADER = AccountAuthenticationHeaders.PASSWORD
        private const val FIELD_LOGIN_ID = "loginId"
        private const val FIELD_EMAIL = "email"
        private const val FIELD_PASSWORD = "password"
        private const val FIELD_CURRENT_PASSWORD = "currentPassword"
        private const val FIELD_NEW_PASSWORD = "newPassword"
        private const val FIELD_NAME = "name"
        private const val FIELD_BIRTH_DATE = "birthDate"
        private const val IS_SUCCESS_PATH = "$.isSuccess"
        private const val STATUS_PATH = "$.status"
        private const val CODE_PATH = "$.code"
        private const val DATA_LOGIN_ID_PATH = "$.data.loginId"
        private const val DATA_EMAIL_PATH = "$.data.email"
        private const val LOGIN_ID = "shoeone96"
        private const val CREATE_LOGIN_ID = "shoeone97"
        private const val INVALID_LOGIN_ID = "shoeone!"
        private const val EMAIL = "user@example.com"
        private const val CREATE_EMAIL = "create@example.com"
        private const val RAW_PASSWORD = "abf15!@#"
        private const val NEW_PASSWORD = "cdg26!@#"
        private const val WRONG_PASSWORD = "wrong15!@#"
        private const val ACCOUNT_NAME = "홍길동"
        private val UNAUTHORIZED_STATUS = HttpStatus.UNAUTHORIZED.value()
        private val UNAUTHORIZED_CODE = CommonErrorCode.UNAUTHORIZED.code
        private val BIRTH_DATE: LocalDate = LocalDate.of(1996, 1, 1)
    }
}
