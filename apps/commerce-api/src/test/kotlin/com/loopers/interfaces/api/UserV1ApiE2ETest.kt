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
        private const val INFO_ENDPOINT = "/api/v1/user/info"
        private const val CHANGE_PASSWORD_ENDPOINT = "/api/v1/user/password"
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

    private fun putChangePassword(
        loginId: String? = null,
        loginPw: String? = null,
        body: Map<String, String>,
    ): org.springframework.http.ResponseEntity<ApiResponse<Any>> {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            loginId?.let { set("X-Loopers-LoginId", it) }
            loginPw?.let { set("X-Loopers-LoginPw", it) }
        }
        val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}
        return testRestTemplate.exchange(CHANGE_PASSWORD_ENDPOINT, HttpMethod.PUT, HttpEntity(body, headers), responseType)
    }

    private fun getInfo(
        loginId: String? = null,
        loginPw: String? = null,
    ): org.springframework.http.ResponseEntity<ApiResponse<Any>> {
        val headers = HttpHeaders().apply {
            loginId?.let { set("X-Loopers-LoginId", it) }
            loginPw?.let { set("X-Loopers-LoginPw", it) }
        }
        val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}
        return testRestTemplate.exchange(INFO_ENDPOINT, HttpMethod.GET, HttpEntity<Any>(headers), responseType)
    }

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

    @DisplayName("PUT /api/v1/user/password")
    @Nested
    inner class ChangePassword {

        private fun createUser(
            id: String = "testuser01",
            pw: String = "password1234",
            name: String = "홍길동",
            birth: String = "1995-03-15",
            email: String = "test@example.com",
        ) {
            postSignup(signupRequest(id = id, pw = pw, name = name, birth = birth, email = email))
        }

        @DisplayName("유효한 인증 헤더와 올바른 기존 비밀번호로 요청하면, 성공 응답을 받고 변경된 비밀번호로 로그인할 수 있다.")
        @Test
        fun returnsSuccess_whenValidRequest() {
            // arrange
            createUser()
            val body = mapOf(
                "oldPassword" to "password1234",
                "newPassword" to "newPassword1234",
            )

            // act
            val response = putChangePassword(loginId = "testuser01", loginPw = "password1234", body = body)

            // assert — 비밀번호 변경 성공
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
            )

            // assert — 기존 비밀번호로 로그인 실패
            val oldPwResponse = getInfo(loginId = "testuser01", loginPw = "password1234")
            assertThat(oldPwResponse.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)

            // assert — 변경된 비밀번호로 로그인 성공
            val newPwResponse = getInfo(loginId = "testuser01", loginPw = "newPassword1234")
            assertThat(newPwResponse.statusCode.is2xxSuccessful).isTrue()
        }

        @DisplayName("존재하지 않는 유저로 요청하면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        fun returnsUnauthorized_whenUserNotExists() {
            // arrange
            val body = mapOf(
                "oldPassword" to "password1234",
                "newPassword" to "newPassword1234",
            )

            // act
            val response = putChangePassword(loginId = "testuser01", loginPw = "password1234", body = body)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }

        @DisplayName("기존 비밀번호가 틀리면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenOldPasswordIsWrong() {
            // arrange
            createUser()
            val body = mapOf(
                "oldPassword" to "wrongOldPassword1",
                "newPassword" to "newPassword1234",
            )

            // act
            val response = putChangePassword(loginId = "testuser01", loginPw = "password1234", body = body)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }
    }

    @DisplayName("GET /api/v1/user/info")
    @Nested
    inner class GetMyInfo {

        private fun createUser(
            id: String = "testuser01",
            pw: String = "password1234",
            name: String = "홍길동",
            birth: String = "1995-03-15",
            email: String = "test@example.com",
        ) {
            postSignup(signupRequest(id = id, pw = pw, name = name, birth = birth, email = email))
        }

        @DisplayName("유효한 헤더로 내 정보를 조회하면, 성공 응답을 받는다.")
        @Test
        fun returnsSuccess_whenValidHeadersAreProvided() {
            // arrange
            createUser()

            // act
            val response = getInfo(loginId = "testuser01", loginPw = "password1234")

            // assert
            val data = response.body?.data as? Map<*, *>
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
                { assertThat(data?.get("loginId")).isEqualTo("testuser01") },
                { assertThat(data?.get("email")).isEqualTo("test@example.com") },
                { assertThat(data?.get("birth")).isEqualTo("1995-03-15") },
            )
        }

        @DisplayName("이름의 마지막 글자가 마스킹되어 반환된다.")
        @Test
        fun returnsmaskedName_whenValidHeadersAreProvided() {
            // arrange
            createUser(name = "홍길동")

            // act
            val response = getInfo(loginId = "testuser01", loginPw = "password1234")

            // assert
            val data = response.body?.data as? Map<*, *>
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(data?.get("name")).isEqualTo("홍길*") },
            )
        }

        @DisplayName("로그인에 실패하면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        fun returnsUnauthorized_whenLoginFails() {
            // arrange
            createUser()

            // act
            val response = getInfo(loginId = "testuser01", loginPw = "wrongPassword1")

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }
    }
}
