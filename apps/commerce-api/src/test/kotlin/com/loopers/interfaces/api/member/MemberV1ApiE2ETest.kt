package com.loopers.interfaces.api.member

import com.loopers.interfaces.api.ApiResponse
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MemberV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val ENDPOINT_REGISTER = "/api/v1/members/register"
        private const val ENDPOINT_MY_INFO = "/api/v1/members/me"
        private const val ENDPOINT_CHANGE_PASSWORD = "/api/v1/members/password"

        private const val LOGIN_ID = "user01"
        private const val PASSWORD = "Password1!"
        private const val NEW_PASSWORD = "NewPass1!"
        private const val NAME = "홍길동"
        private const val BIRTH_DATE = "19900628"
        private const val EMAIL = "test@test.com"
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("POST /api/v1/members/register")
    @Nested
    inner class Register {

        @DisplayName("유효한 정보로 회원가입하면, 201 응답과 회원 정보를 반환한다.")
        @Test
        fun returnsRegisterResponse_whenValidInfoIsProvided() {
            // arrange
            val request = MemberV1Dto.RegisterRequest(
                loginId = LOGIN_ID,
                password = PASSWORD,
                name = NAME,
                birthDate = BIRTH_DATE,
                email = EMAIL,
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<MemberV1Dto.RegisterResponse>>() {}
            val response = testRestTemplate.exchange(ENDPOINT_REGISTER, HttpMethod.POST, HttpEntity(request), responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.loginId).isEqualTo(LOGIN_ID) },
                { assertThat(response.body?.data?.name).isEqualTo(NAME) },
                { assertThat(response.body?.data?.email).isEqualTo(EMAIL) },
            )
        }

        @DisplayName("이미 가입된 loginId로 가입하면, 409 CONFLICT 응답을 받는다.")
        @Test
        fun returnsConflict_whenLoginIdAlreadyExists() {
            // arrange
            val request = MemberV1Dto.RegisterRequest(
                loginId = LOGIN_ID,
                password = PASSWORD,
                name = NAME,
                birthDate = BIRTH_DATE,
                email = EMAIL,
            )
            testRestTemplate.exchange(ENDPOINT_REGISTER, HttpMethod.POST, HttpEntity(request), responseType())

            // act
            val response = testRestTemplate.exchange(ENDPOINT_REGISTER, HttpMethod.POST, HttpEntity(request), responseType())

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        }

        @DisplayName("유효하지 않은 정보로 가입하면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenInvalidInfoIsProvided() {
            // arrange
            val request = MemberV1Dto.RegisterRequest(
                loginId = "",
                password = PASSWORD,
                name = NAME,
                birthDate = BIRTH_DATE,
                email = EMAIL,
            )

            // act
            val response = testRestTemplate.exchange(ENDPOINT_REGISTER, HttpMethod.POST, HttpEntity(request), responseType())

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        private fun responseType() = object : ParameterizedTypeReference<ApiResponse<MemberV1Dto.RegisterResponse>>() {}
    }

    @DisplayName("GET /api/v1/members/me")
    @Nested
    inner class GetMyInfo {

        @DisplayName("올바른 인증 헤더로 조회하면, 200 응답과 이름이 마스킹된 내 정보를 반환한다.")
        @Test
        fun returnsMyInfoWithMaskedName_whenValidCredentialsAreProvided() {
            // arrange
            testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                HttpEntity(MemberV1Dto.RegisterRequest(LOGIN_ID, PASSWORD, NAME, BIRTH_DATE, EMAIL)),
                object : ParameterizedTypeReference<ApiResponse<MemberV1Dto.RegisterResponse>>() {},
            )

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT_MY_INFO,
                HttpMethod.GET,
                HttpEntity<Void>(authHeaders(LOGIN_ID, PASSWORD)),
                object : ParameterizedTypeReference<ApiResponse<MemberV1Dto.MyInfoResponse>>() {},
            )

            // assert
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.loginId).isEqualTo(LOGIN_ID) },
                { assertThat(response.body?.data?.name).isEqualTo("홍길*") },
                { assertThat(response.body?.data?.birthDate).isEqualTo(BIRTH_DATE) },
                { assertThat(response.body?.data?.email).isEqualTo(EMAIL) },
            )
        }

        @DisplayName("존재하지 않는 loginId로 조회하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenLoginIdDoesNotExist() {
            // act
            val response = testRestTemplate.exchange(
                ENDPOINT_MY_INFO,
                HttpMethod.GET,
                HttpEntity<Void>(authHeaders("nonexistent", PASSWORD)),
                object : ParameterizedTypeReference<ApiResponse<MemberV1Dto.MyInfoResponse>>() {},
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("비밀번호가 틀리면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        fun returnsUnauthorized_whenPasswordIsWrong() {
            // arrange
            testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                HttpEntity(MemberV1Dto.RegisterRequest(LOGIN_ID, PASSWORD, NAME, BIRTH_DATE, EMAIL)),
                object : ParameterizedTypeReference<ApiResponse<MemberV1Dto.RegisterResponse>>() {},
            )

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT_MY_INFO,
                HttpMethod.GET,
                HttpEntity<Void>(authHeaders(LOGIN_ID, "WrongPass1!")),
                object : ParameterizedTypeReference<ApiResponse<MemberV1Dto.MyInfoResponse>>() {},
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        private fun authHeaders(loginId: String, password: String): HttpHeaders =
            HttpHeaders().apply {
                set("X-Loopers-LoginId", loginId)
                set("X-Loopers-LoginPw", password)
            }
    }

    @DisplayName("PATCH /api/v1/members/password")
    @Nested
    inner class ChangePassword {

        @DisplayName("올바른 인증 헤더와 유효한 새 비밀번호로 변경하면, 200 응답을 반환한다.")
        @Test
        fun returnsOk_whenValidCredentialsAndNewPasswordAreProvided() {
            // arrange
            testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                HttpEntity(MemberV1Dto.RegisterRequest(LOGIN_ID, PASSWORD, NAME, BIRTH_DATE, EMAIL)),
                object : ParameterizedTypeReference<ApiResponse<MemberV1Dto.RegisterResponse>>() {},
            )

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT_CHANGE_PASSWORD,
                HttpMethod.PATCH,
                HttpEntity(MemberV1Dto.ChangePasswordRequest(NEW_PASSWORD), authHeaders(LOGIN_ID, PASSWORD)),
                object : ParameterizedTypeReference<ApiResponse<Unit>>() {},
            )

            // assert
            assertThat(response.statusCode.is2xxSuccessful).isTrue()
        }

        @DisplayName("현재 비밀번호와 동일한 비밀번호로 변경하면, 409 CONFLICT 응답을 받는다.")
        @Test
        fun returnsConflict_whenNewPasswordIsSameAsCurrent() {
            // arrange
            testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                HttpEntity(MemberV1Dto.RegisterRequest(LOGIN_ID, PASSWORD, NAME, BIRTH_DATE, EMAIL)),
                object : ParameterizedTypeReference<ApiResponse<MemberV1Dto.RegisterResponse>>() {},
            )

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT_CHANGE_PASSWORD,
                HttpMethod.PATCH,
                HttpEntity(MemberV1Dto.ChangePasswordRequest(PASSWORD), authHeaders(LOGIN_ID, PASSWORD)),
                object : ParameterizedTypeReference<ApiResponse<Unit>>() {},
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        }

        @DisplayName("유효하지 않은 새 비밀번호로 변경하면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenNewPasswordIsInvalid() {
            // arrange
            testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                HttpEntity(MemberV1Dto.RegisterRequest(LOGIN_ID, PASSWORD, NAME, BIRTH_DATE, EMAIL)),
                object : ParameterizedTypeReference<ApiResponse<MemberV1Dto.RegisterResponse>>() {},
            )

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT_CHANGE_PASSWORD,
                HttpMethod.PATCH,
                HttpEntity(MemberV1Dto.ChangePasswordRequest("short"), authHeaders(LOGIN_ID, PASSWORD)),
                object : ParameterizedTypeReference<ApiResponse<Unit>>() {},
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("비밀번호가 틀리면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        fun returnsUnauthorized_whenPasswordIsWrong() {
            // arrange
            testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                HttpEntity(MemberV1Dto.RegisterRequest(LOGIN_ID, PASSWORD, NAME, BIRTH_DATE, EMAIL)),
                object : ParameterizedTypeReference<ApiResponse<MemberV1Dto.RegisterResponse>>() {},
            )

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT_CHANGE_PASSWORD,
                HttpMethod.PATCH,
                HttpEntity(MemberV1Dto.ChangePasswordRequest(NEW_PASSWORD), authHeaders(LOGIN_ID, "WrongPass1!")),
                object : ParameterizedTypeReference<ApiResponse<Unit>>() {},
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        private fun authHeaders(loginId: String, password: String): HttpHeaders =
            HttpHeaders().apply {
                set("X-Loopers-LoginId", loginId)
                set("X-Loopers-LoginPw", password)
            }
    }
}
