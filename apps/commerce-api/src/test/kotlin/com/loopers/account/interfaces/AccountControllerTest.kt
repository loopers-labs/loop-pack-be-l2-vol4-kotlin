package com.loopers.account.interfaces

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.account.application.AccountCreateCommand
import com.loopers.account.application.AccountService
import com.loopers.account.infrastructure.security.AccountAuthenticationAttributes
import com.loopers.support.DatabaseCleanup
import java.time.LocalDate
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class AccountControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val accountService: AccountService,
    private val jdbcTemplate: JdbcTemplate,
    private val databaseCleanup: DatabaseCleanup,
) {
    @BeforeEach
    fun cleanup() {
        databaseCleanup.execute()
    }

    @DisplayName("POST /api/v1/users")
    @Nested
    inner class Create {
        @DisplayName("유효한 회원가입 요청이면 성공 응답을 반환한다.")
        @Test
        fun returnsSuccess_whenValidRequestIsProvided() {
            mockMvc.performCreate(validCreateRequest())
                .andExpect(status().isOk)
                .andExpect(jsonPath(JSON_IS_SUCCESS).value(true))
        }

        @DisplayName("생년월일이 날짜 형식이 아니면 400 BAD_REQUEST 응답을 반환한다.")
        @Test
        fun returnsBadRequest_whenBirthDateIsInvalid() {
            val request = validCreateRequest() + (FIELD_BIRTH_DATE to INVALID_BIRTH_DATE)

            mockMvc.performCreate(request)
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath(JSON_IS_SUCCESS).value(false))
        }

        @DisplayName("비밀번호 형식이 유효하지 않으면 400 BAD_REQUEST 응답을 반환한다.")
        @Test
        fun returnsBadRequest_whenPasswordIsInvalid() {
            val request = validCreateRequest() + (FIELD_PASSWORD to INVALID_PASSWORD)

            mockMvc.performCreate(request)
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath(JSON_IS_SUCCESS).value(false))
        }

        @DisplayName("이메일 형식이 유효하지 않으면 400 BAD_REQUEST 응답을 반환한다.")
        @Test
        fun returnsBadRequest_whenEmailIsInvalid() {
            val request = validCreateRequest() + (FIELD_EMAIL to INVALID_EMAIL)

            mockMvc.performCreate(request)
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath(JSON_IS_SUCCESS).value(false))
        }

        @DisplayName("로그인 ID 형식이 유효하지 않으면 400 BAD_REQUEST 응답을 반환한다.")
        @Test
        fun returnsBadRequest_whenLoginIdIsInvalid() {
            val request = validCreateRequest() + (FIELD_LOGIN_ID to INVALID_LOGIN_ID)

            mockMvc.performCreate(request)
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath(JSON_IS_SUCCESS).value(false))
        }
    }

    @DisplayName("GET /api/v1/users/me")
    @Nested
    inner class Me {
        @DisplayName("인증된 account 기준으로 내 정보를 반환한다.")
        @Test
        fun returnsMyAccountInfo_whenAccountIsAuthenticated() {
            accountService.create(
                AccountCreateCommand(
                    loginId = ME_LOGIN_ID,
                    email = ME_EMAIL,
                    password = VALID_PASSWORD,
                    name = ACCOUNT_NAME,
                    birthDate = BIRTH_DATE,
                ),
            )
            val accountId = jdbcTemplate.queryForObject(
                SELECT_ACCOUNT_ID_BY_EMAIL,
                Long::class.java,
                ME_EMAIL,
            ) ?: error(ACCOUNT_ID_NOT_FOUND_MESSAGE)

            mockMvc.performGetMe(accountId, ME_LOGIN_ID)
                .andExpect(status().isOk)
                .andExpect(jsonPath(JSON_IS_SUCCESS).value(true))
                .andExpect(jsonPath(JSON_DATA_LOGIN_ID).value(ME_LOGIN_ID))
                .andExpect(jsonPath(JSON_DATA_NAME).value(MASKED_ACCOUNT_NAME))
                .andExpect(jsonPath(JSON_DATA_BIRTH_DATE).value(BIRTH_DATE.toString()))
                .andExpect(jsonPath(JSON_DATA_EMAIL).value(ME_EMAIL))
        }
    }

    @DisplayName("PUT /api/v1/users/password")
    @Nested
    inner class ChangePassword {
        @DisplayName("기존 비밀번호와 새 비밀번호가 유효하면 성공 응답을 반환한다.")
        @Test
        fun returnsSuccess_whenPasswordChangeRequestIsValid() {
            accountService.create(
                AccountCreateCommand(
                    loginId = PASSWORD_CHANGE_LOGIN_ID,
                    email = PASSWORD_CHANGE_EMAIL,
                    password = VALID_PASSWORD,
                    name = ACCOUNT_NAME,
                    birthDate = BIRTH_DATE,
                ),
            )

            mockMvc.performChangePassword(
                loginId = PASSWORD_CHANGE_LOGIN_ID,
                request = validPasswordChangeRequest(),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath(JSON_IS_SUCCESS).value(true))
        }

        @DisplayName("새 비밀번호 형식이 유효하지 않으면 400 BAD_REQUEST 응답을 반환한다.")
        @Test
        fun returnsBadRequest_whenNewPasswordIsInvalid() {
            accountService.create(
                AccountCreateCommand(
                    loginId = INVALID_NEW_PASSWORD_LOGIN_ID,
                    email = INVALID_NEW_PASSWORD_EMAIL,
                    password = VALID_PASSWORD,
                    name = ACCOUNT_NAME,
                    birthDate = BIRTH_DATE,
                ),
            )
            val request = validPasswordChangeRequest() + (FIELD_NEW_PASSWORD to INVALID_PASSWORD)

            mockMvc.performChangePassword(
                loginId = INVALID_NEW_PASSWORD_LOGIN_ID,
                request = request,
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath(JSON_IS_SUCCESS).value(false))
        }

        @DisplayName("기존 비밀번호가 일치하지 않으면 401 UNAUTHORIZED 응답을 반환한다.")
        @Test
        fun returnsUnauthorized_whenCurrentPasswordDoesNotMatch() {
            accountService.create(
                AccountCreateCommand(
                    loginId = WRONG_CURRENT_PASSWORD_LOGIN_ID,
                    email = WRONG_CURRENT_PASSWORD_EMAIL,
                    password = VALID_PASSWORD,
                    name = ACCOUNT_NAME,
                    birthDate = BIRTH_DATE,
                ),
            )
            val request = validPasswordChangeRequest() + (FIELD_CURRENT_PASSWORD to WRONG_CURRENT_PASSWORD)

            mockMvc.performChangePassword(
                loginId = WRONG_CURRENT_PASSWORD_LOGIN_ID,
                request = request,
            )
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath(JSON_IS_SUCCESS).value(false))
        }
    }

    private fun MockMvc.performGetMe(
        accountId: Long,
        loginId: String,
    ) = perform(
        get(ACCOUNT_ME_PATH)
            .requestAttr(AccountAuthenticationAttributes.ACCOUNT_ID, accountId)
            .requestAttr(AccountAuthenticationAttributes.LOGIN_ID, loginId),
    )

    private fun MockMvc.performChangePassword(
        loginId: String,
        request: Map<String, String>,
    ) = perform(
        put(ACCOUNT_PASSWORD_PATH)
            .requestAttr(AccountAuthenticationAttributes.LOGIN_ID, loginId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)),
    )

    private fun MockMvc.performCreate(request: Map<String, String>) =
        perform(
            post(ACCOUNTS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )

    private fun validCreateRequest(): Map<String, String> =
        mapOf(
            FIELD_LOGIN_ID to VALID_LOGIN_ID,
            FIELD_EMAIL to VALID_EMAIL,
            FIELD_PASSWORD to VALID_PASSWORD,
            FIELD_NAME to ACCOUNT_NAME,
            FIELD_BIRTH_DATE to BIRTH_DATE.toString(),
        )

    private fun validPasswordChangeRequest(): Map<String, String> =
        mapOf(
            FIELD_CURRENT_PASSWORD to VALID_PASSWORD,
            FIELD_NEW_PASSWORD to NEW_PASSWORD,
        )

    private companion object {
        private const val ACCOUNTS_PATH = "/api/v1/users"
        private const val ACCOUNT_ME_PATH = "/api/v1/users/me"
        private const val ACCOUNT_PASSWORD_PATH = "/api/v1/users/password"

        private const val FIELD_LOGIN_ID = "loginId"
        private const val FIELD_EMAIL = "email"
        private const val FIELD_PASSWORD = "password"
        private const val FIELD_CURRENT_PASSWORD = "currentPassword"
        private const val FIELD_NEW_PASSWORD = "newPassword"
        private const val FIELD_NAME = "name"
        private const val FIELD_BIRTH_DATE = "birthDate"

        private const val JSON_IS_SUCCESS = "$.isSuccess"
        private const val JSON_DATA_LOGIN_ID = "$.data.loginId"
        private const val JSON_DATA_NAME = "$.data.name"
        private const val JSON_DATA_BIRTH_DATE = "$.data.birthDate"
        private const val JSON_DATA_EMAIL = "$.data.email"

        private const val VALID_LOGIN_ID = "shoeone96"
        private const val ME_LOGIN_ID = "meuser96"
        private const val PASSWORD_CHANGE_LOGIN_ID = "password96"
        private const val INVALID_NEW_PASSWORD_LOGIN_ID = "newpw96"
        private const val WRONG_CURRENT_PASSWORD_LOGIN_ID = "wrongpw96"
        private const val VALID_EMAIL = "user@example.com"
        private const val ME_EMAIL = "me@example.com"
        private const val PASSWORD_CHANGE_EMAIL = "password@example.com"
        private const val INVALID_NEW_PASSWORD_EMAIL = "newpw@example.com"
        private const val WRONG_CURRENT_PASSWORD_EMAIL = "wrongpw@example.com"
        private const val VALID_PASSWORD = "abf15!@#"
        private const val NEW_PASSWORD = "cdg26!@#"
        private const val INVALID_PASSWORD = "abc123!"
        private const val WRONG_CURRENT_PASSWORD = "wrong15!@#"
        private const val ACCOUNT_NAME = "홍길동"
        private const val MASKED_ACCOUNT_NAME = "홍길*"
        private const val INVALID_EMAIL = "user@"
        private const val INVALID_LOGIN_ID = "shoeone!"
        private const val INVALID_BIRTH_DATE = "1996-13-40"
        private val BIRTH_DATE: LocalDate = LocalDate.of(1996, 1, 1)

        private const val SELECT_ACCOUNT_ID_BY_EMAIL = "select id from account where email = ?"
        private const val ACCOUNT_ID_NOT_FOUND_MESSAGE = "account id must exist"
    }
}
