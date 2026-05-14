package com.loopers.interfaces.api.member

import com.loopers.infrastructure.member.MemberJpaRepository
import com.loopers.interfaces.api.ApiResponse
import com.loopers.utils.DatabaseCleanUp
import org.junit.jupiter.api.AfterEach
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.time.LocalDate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MemberV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val memberJpaRepository: MemberJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("POST /api/v1/members")
    @Nested
    inner class SignUp {
        @DisplayName("회원가입 요청이 유효하면 회원가입에 성공한다")
        @Test
        fun returnsSuccess_whenSignUpRequestIsValid() {
            val request = createSignUpRequest()

            val response = testRestTemplate.exchange(
                SIGN_UP_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(request),
                object : ParameterizedTypeReference<ApiResponse<MemberV1Dto.SignUpResponse>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.loginId).isEqualTo(request.loginId) },
                { assertThat(memberJpaRepository.existsByLoginId(request.loginId)).isTrue() },
            )
        }

        @DisplayName("이미 가입된 로그인 ID 로 회원가입하면 실패한다")
        @Test
        fun returnsConflict_whenLoginIdAlreadyExists() {
            val request = createSignUpRequest(loginId = "loopers123")
            testRestTemplate.postForEntity(SIGN_UP_ENDPOINT, request, String::class.java)

            val response = testRestTemplate.exchange(
                SIGN_UP_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(createSignUpRequest(loginId = "loopers123", email = "other@gmail.com")),
                object : ParameterizedTypeReference<ApiResponse<MemberV1Dto.SignUpResponse>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        }
    }

    @DisplayName("GET /api/v1/members/me")
    @Nested
    inner class GetMyInfo {
        @DisplayName("로그인 ID 와 비밀번호가 유효하면 마스킹된 회원 정보를 반환한다")
        @Test
        fun returnsMaskedMemberInfo_whenCredentialsAreValid() {
            val request = createSignUpRequest(name = "gunyoung")
            testRestTemplate.postForEntity(SIGN_UP_ENDPOINT, request, String::class.java)

            val response = testRestTemplate.exchange(
                GET_MY_INFO_ENDPOINT,
                HttpMethod.GET,
                HttpEntity(Unit, createAuthHeaders(request.loginId, request.password)),
                object : ParameterizedTypeReference<ApiResponse<MemberV1Dto.MyInfoResponse>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.loginId).isEqualTo(request.loginId) },
                { assertThat(response.body?.data?.name).isEqualTo("gunyoun*") },
                { assertThat(response.body?.data?.birthDate).isEqualTo(request.birthDate) },
                { assertThat(response.body?.data?.email).isEqualTo(request.email) },
            )
        }

        @DisplayName("가입되지 않은 로그인 ID 로 조회하면 실패한다")
        @Test
        fun returnsNotFound_whenLoginIdDoesNotExist() {
            val response = testRestTemplate.exchange(
                GET_MY_INFO_ENDPOINT,
                HttpMethod.GET,
                HttpEntity(Unit, createAuthHeaders("loopers-123", "Loopers123!")),
                object : ParameterizedTypeReference<ApiResponse<MemberV1Dto.MyInfoResponse>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("비밀번호가 일치하지 않으면 실패한다")
        @Test
        fun returnsUnauthorized_whenPasswordDoesNotMatch() {
            val request = createSignUpRequest()
            testRestTemplate.postForEntity(SIGN_UP_ENDPOINT, request, String::class.java)

            val response = testRestTemplate.exchange(
                GET_MY_INFO_ENDPOINT,
                HttpMethod.GET,
                HttpEntity(Unit, createAuthHeaders(request.loginId, "Wrong123!")),
                object : ParameterizedTypeReference<ApiResponse<MemberV1Dto.MyInfoResponse>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }

    @DisplayName("PATCH /api/v1/members/me/password")
    @Nested
    inner class UpdatePassword {
        @DisplayName("기존 비밀번호가 일치하고 새 비밀번호가 유효하면 비밀번호 수정에 성공한다")
        @Test
        fun returnsSuccess_whenCurrentPasswordMatchesAndNewPasswordIsValid() {
            val request = createSignUpRequest(password = "Loopers123!")
            testRestTemplate.postForEntity(SIGN_UP_ENDPOINT, request, String::class.java)

            val response = testRestTemplate.exchange(
                UPDATE_PASSWORD_ENDPOINT,
                HttpMethod.PATCH,
                HttpEntity(
                    MemberV1Dto.UpdatePasswordRequest(newPassword = "NewLoopers1!"),
                    createAuthHeaders(request.loginId, request.password),
                ),
                String::class.java,
            )

            val oldPasswordResponse = testRestTemplate.exchange(
                GET_MY_INFO_ENDPOINT,
                HttpMethod.GET,
                HttpEntity(Unit, createAuthHeaders(request.loginId, request.password)),
                String::class.java,
            )
            val newPasswordResponse = testRestTemplate.exchange(
                GET_MY_INFO_ENDPOINT,
                HttpMethod.GET,
                HttpEntity(Unit, createAuthHeaders(request.loginId, "NewLoopers1!")),
                object : ParameterizedTypeReference<ApiResponse<MemberV1Dto.MyInfoResponse>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(oldPasswordResponse.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED) },
                { assertThat(newPasswordResponse.statusCode).isEqualTo(HttpStatus.OK) },
            )
        }

        @DisplayName("기존 비밀번호가 일치하지 않으면 실패한다")
        @Test
        fun returnsUnauthorized_whenCurrentPasswordDoesNotMatch() {
            val request = createSignUpRequest(password = "Loopers123!")
            testRestTemplate.postForEntity(SIGN_UP_ENDPOINT, request, String::class.java)

            val response = testRestTemplate.exchange(
                UPDATE_PASSWORD_ENDPOINT,
                HttpMethod.PATCH,
                HttpEntity(
                    MemberV1Dto.UpdatePasswordRequest(newPassword = "NewLoopers1!"),
                    createAuthHeaders(request.loginId, "Wrong123!"),
                ),
                String::class.java,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @DisplayName("새 비밀번호가 정책을 만족하지 않으면 실패한다")
        @Test
        fun returnsBadRequest_whenNewPasswordDoesNotSatisfyPolicy() {
            val request = createSignUpRequest(password = "Loopers123!")
            testRestTemplate.postForEntity(SIGN_UP_ENDPOINT, request, String::class.java)

            val response = testRestTemplate.exchange(
                UPDATE_PASSWORD_ENDPOINT,
                HttpMethod.PATCH,
                HttpEntity(
                    MemberV1Dto.UpdatePasswordRequest(newPassword = "short"),
                    createAuthHeaders(request.loginId, request.password),
                ),
                String::class.java,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("현재 비밀번호와 같은 비밀번호로 변경하면 실패한다")
        @Test
        fun returnsBadRequest_whenNewPasswordIsSameAsCurrentPassword() {
            val request = createSignUpRequest(password = "Loopers123!")
            testRestTemplate.postForEntity(SIGN_UP_ENDPOINT, request, String::class.java)

            val response = testRestTemplate.exchange(
                UPDATE_PASSWORD_ENDPOINT,
                HttpMethod.PATCH,
                HttpEntity(
                    MemberV1Dto.UpdatePasswordRequest(newPassword = request.password),
                    createAuthHeaders(request.loginId, request.password),
                ),
                String::class.java,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }

    private fun createSignUpRequest(
        loginId: String = "loopers123",
        password: String = "Loopers123!",
        name: String = "gunyoung",
        birthDate: LocalDate = LocalDate.of(1995, 5, 20),
        email: String = "loopers@gmail.com",
    ): MemberV1Dto.SignUpRequest =
        MemberV1Dto.SignUpRequest(
            loginId = loginId,
            password = password,
            name = name,
            birthDate = birthDate,
            email = email,
        )

    private fun createAuthHeaders(
        loginId: String,
        password: String,
    ): HttpHeaders =
        HttpHeaders().apply {
            set(LOGIN_ID_HEADER, loginId)
            set(LOGIN_PW_HEADER, password)
        }

    companion object {
        private const val SIGN_UP_ENDPOINT = "/api/v1/members"
        private const val GET_MY_INFO_ENDPOINT = "/api/v1/members/me"
        private const val UPDATE_PASSWORD_ENDPOINT = "/api/v1/members/me/password"
        private const val LOGIN_ID_HEADER = "X-Loopers-LoginId"
        private const val LOGIN_PW_HEADER = "X-Loopers-LoginPw"
    }
}
