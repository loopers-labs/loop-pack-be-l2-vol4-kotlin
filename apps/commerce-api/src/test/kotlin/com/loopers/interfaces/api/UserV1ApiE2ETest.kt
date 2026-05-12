package com.loopers.interfaces.api

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
import org.springframework.http.MediaType

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val SIGNUP_ENDPOINT = "/api/v1/user/signup"
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun signupRequest(
        id: String = "testuser01",
        pw: String = "password1234",
        name: String = "홍길동",
        birth: String = "1995-03-15",
        email: String = "test@example.com",
    ): Map<String, String> = mapOf(
        "id" to id,
        "pw" to pw,
        "name" to name,
        "birth" to birth,
        "email" to email,
    )

    private fun postSignup(body: Map<String, String>): org.springframework.http.ResponseEntity<ApiResponse<Any>> {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}
        return testRestTemplate.exchange(SIGNUP_ENDPOINT, HttpMethod.POST, HttpEntity(body, headers), responseType)
    }

    @DisplayName("POST /api/v1/user/signup")
    @Nested
    inner class Signup {

        @DisplayName("유효한 정보로 회원가입하면, 성공 응답을 받는다.")
        @Test
        fun returnsSuccess_whenValidRequestIsProvided() {
            // arrange
            val request = signupRequest()

            // act
            val response = postSignup(request)

            // assert
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
            )
        }

        @DisplayName("유효성 검사가 실패되는 경우, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenPasswordIsTooShort() {
            // arrange
            val request = signupRequest(pw = "short77")

            // act
            val response = postSignup(request)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }

        @DisplayName("중복 ID로 회원가입 하는 경우, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenDuplicateIdIsProvided() {
            // arrange
            val request = signupRequest(id = "duplicateUser")
            postSignup(request) // 첫 번째 가입 — 성공

            // act
            val response = postSignup(request) // 두 번째 가입 — 실패

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }
    }
}
