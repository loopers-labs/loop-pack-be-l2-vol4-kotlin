package com.loopers.interfaces.api.user

import com.loopers.infrastructure.user.UserJpaRepository
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
class UserV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val userJpaRepository: UserJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("POST /api/v1/users")
    @Nested
    inner class SignUp {
        @DisplayName("유효한 입력으로 가입하면, 200 응답과 마스킹된 이름의 MyInfoResponse 가 반환된다.")
        @Test
        fun returnsMaskedMyInfo_whenValidSignUp() {
            val body = mapOf(
                "loginId" to "loopers01",
                "password" to "abcd1234",
                "name" to "홍길동",
                "birthdate" to "1990-01-01",
                "email" to "user@example.com",
            )
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.MyInfoResponse>>() {}

            val response = testRestTemplate.exchange("/api/v1/users", HttpMethod.POST, HttpEntity(body), responseType)

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.loginId).isEqualTo("loopers01") },
                { assertThat(response.body?.data?.name).isEqualTo("홍길*") },
                { assertThat(response.body?.data?.email).isEqualTo("user@example.com") },
                { assertThat(userJpaRepository.existsByLoginId("loopers01")).isTrue() },
            )
        }

        @DisplayName("이메일 형식이 잘못되면, 400 응답을 반환한다.")
        @Test
        fun returnsBadRequest_whenEmailMalformed() {
            val body = mapOf(
                "loginId" to "loopers01",
                "password" to "abcd1234",
                "name" to "홍길동",
                "birthdate" to "1990-01-01",
                "email" to "not-an-email",
            )
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.MyInfoResponse>>() {}

            val response = testRestTemplate.exchange("/api/v1/users", HttpMethod.POST, HttpEntity(body), responseType)

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("이미 사용 중인 loginId 로 가입하면, 409 응답을 반환한다.")
        @Test
        fun returnsConflict_whenLoginIdTaken() {
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.MyInfoResponse>>() {}
            val first = mapOf(
                "loginId" to "loopers01",
                "password" to "abcd1234",
                "name" to "홍길동",
                "birthdate" to "1990-01-01",
                "email" to "user@example.com",
            )
            testRestTemplate.exchange("/api/v1/users", HttpMethod.POST, HttpEntity(first), responseType)

            val second = first + ("password" to "wxyz5678")
            val response = testRestTemplate.exchange("/api/v1/users", HttpMethod.POST, HttpEntity(second), responseType)

            assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        }
    }

    @DisplayName("GET /api/v1/users/me")
    @Nested
    inner class GetMyInfo {
        private val infoType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.MyInfoResponse>>() {}

        private fun registerDefault() {
            val body = mapOf(
                "loginId" to "loopers01",
                "password" to "abcd1234",
                "name" to "홍길동",
                "birthdate" to "1990-01-01",
                "email" to "user@example.com",
            )
            testRestTemplate.exchange("/api/v1/users", HttpMethod.POST, HttpEntity(body), infoType)
        }

        private fun authHeaders(loginId: String, password: String): HttpHeaders = HttpHeaders().apply {
            add("X-Loopers-LoginId", loginId)
            add("X-Loopers-LoginPw", password)
        }

        @DisplayName("올바른 인증 헤더가 있으면, 본인의 마스킹된 정보를 반환한다.")
        @Test
        fun returnsMyInfo_whenAuthenticated() {
            registerDefault()

            val response = testRestTemplate.exchange(
                "/api/v1/users/me",
                HttpMethod.GET,
                HttpEntity<Any>(authHeaders("loopers01", "abcd1234")),
                infoType,
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.loginId).isEqualTo("loopers01") },
                { assertThat(response.body?.data?.name).isEqualTo("홍길*") },
            )
        }

        @DisplayName("인증 헤더가 없으면, 401 응답을 반환한다.")
        @Test
        fun returnsUnauthorized_whenHeadersMissing() {
            val response = testRestTemplate.exchange(
                "/api/v1/users/me",
                HttpMethod.GET,
                HttpEntity<Any>(HttpHeaders()),
                infoType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @DisplayName("비밀번호가 틀리면, 401 응답을 반환한다.")
        @Test
        fun returnsUnauthorized_whenPasswordWrong() {
            registerDefault()

            val response = testRestTemplate.exchange(
                "/api/v1/users/me",
                HttpMethod.GET,
                HttpEntity<Any>(authHeaders("loopers01", "wrongpw1")),
                infoType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }

    @DisplayName("PATCH /api/v1/users/me/password")
    @Nested
    inner class ChangePassword {
        private val unitType = object : ParameterizedTypeReference<ApiResponse<Unit>>() {}
        private val infoType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.MyInfoResponse>>() {}

        private fun registerDefault() {
            val body = mapOf(
                "loginId" to "loopers01",
                "password" to "abcd1234",
                "name" to "홍길동",
                "birthdate" to "1990-01-01",
                "email" to "user@example.com",
            )
            testRestTemplate.exchange("/api/v1/users", HttpMethod.POST, HttpEntity(body), infoType)
        }

        private fun authHeaders(loginId: String, password: String): HttpHeaders = HttpHeaders().apply {
            add("X-Loopers-LoginId", loginId)
            add("X-Loopers-LoginPw", password)
        }

        @DisplayName("올바른 본문과 인증이 있으면, 200 응답과 저장된 해시 변경이 일어난다.")
        @Test
        fun changesPassword_whenValid() {
            registerDefault()
            val body = mapOf("oldPassword" to "abcd1234", "newPassword" to "wxyz5678")
            val headers = authHeaders("loopers01", "abcd1234")

            val response = testRestTemplate.exchange(
                "/api/v1/users/me/password",
                HttpMethod.PATCH,
                HttpEntity(body, headers),
                unitType,
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                {
                    val reAuth = testRestTemplate.exchange(
                        "/api/v1/users/me",
                        HttpMethod.GET,
                        HttpEntity<Any>(authHeaders("loopers01", "wxyz5678")),
                        infoType,
                    )
                    assertThat(reAuth.statusCode).isEqualTo(HttpStatus.OK)
                },
            )
        }

        @DisplayName("oldPassword 가 일치하지 않으면, 400 응답을 반환한다.")
        @Test
        fun returnsBadRequest_whenOldPasswordWrong() {
            registerDefault()
            val body = mapOf("oldPassword" to "wrongold", "newPassword" to "wxyz5678")
            val headers = authHeaders("loopers01", "abcd1234")

            val response = testRestTemplate.exchange(
                "/api/v1/users/me/password",
                HttpMethod.PATCH,
                HttpEntity(body, headers),
                unitType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }
}
