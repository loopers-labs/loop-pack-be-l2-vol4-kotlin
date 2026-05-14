package com.loopers.interfaces.api

import com.loopers.domain.user.EncodedPassword
import com.loopers.domain.user.UserModel
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.interfaces.api.user.UserV1Dto
import com.loopers.utils.DatabaseCleanUp
import java.time.LocalDate
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
    private val userJpaRepository: UserJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val ENDPOINT = "/api/v1/users"
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("POST /api/v1/users")
    @Nested
    inner class SignUp {
        @DisplayName("유효한 정보로 요청하면 201 과 함께 회원 정보를 반환한다.")
        @Test
        fun signUp_whenAllFieldsAreValid() {
            // arrange
            val request = UserV1Dto.SignUpRequest(
                loginId = "seondays",
                password = "Password1!",
                name = "선데이",
                birthDate = LocalDate.of(1990, 1, 1),
                email = "seondays@example.com",
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>>() {}
            val response = testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, jsonEntity(request), responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED) },
                { assertThat(response.body?.data?.loginId).isEqualTo(request.loginId) },
                { assertThat(response.body?.data?.name).isEqualTo(request.name) },
                { assertThat(response.body?.data?.email).isEqualTo(request.email) },
                { assertThat(response.body?.data?.birthDate).isEqualTo(request.birthDate.toString()) },
            )
        }

        @DisplayName("이미 존재하는 로그인 ID 로 요청하면 409 를 반환한다.")
        @Test
        fun signUp_whenLoginIdAlreadyExists() {
            val loginId = "seondays"

            // arrange
            userJpaRepository.save(
                UserModel(
                    loginId = loginId,
                    encodedPassword = EncodedPassword("\$2a\$10\$existingHashedPassword."),
                    name = "기존가입자",
                    birthDate = LocalDate.of(1990, 1, 1),
                    email = "existing@example.com",
                ),
            )

            val request = UserV1Dto.SignUpRequest(
                // 중복 ID
                loginId = loginId,
                password = "Password1!",
                name = "선데이",
                birthDate = LocalDate.of(1990, 1, 1),
                email = "seondays@example.com",
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>>() {}
            val response = testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, jsonEntity(request), responseType)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        }

        @DisplayName("필수 필드가 빈 값이면 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun signUp_whenRequiredFieldIsBlank() {
            // arrange
            val request = UserV1Dto.SignUpRequest(
                loginId = "",
                password = "Password1!",
                name = "선데이",
                birthDate = LocalDate.of(1990, 1, 1),
                email = "seondays@example.com",
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>>() {}
            val response = testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, jsonEntity(request), responseType)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("생년월일이 날짜 형식이 아니면 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun signUp_whenBirthDateIsInvalidFormat() {
            // arrange — LocalDate 타입 필드에 잘못된 문자열을 JSON 으로 직접 주입
            val rawJson = """
                {
                    "loginId": "seondays",
                    "password": "Password1!",
                    "name": "선데이",
                    "birthDate": "not-a-date",
                    "email": "seondays@example.com"
                }
            """.trimIndent()

            // act
            val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>>() {}
            val response = testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, HttpEntity(rawJson, headers), responseType)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }

    @DisplayName("GET /api/v1/users/me")
    @Nested
    inner class GetMe {
        @DisplayName("유효한 헤더로 요청하면 200과 함께 마스킹된 회원 정보를 반환한다.")
        @Test
        fun getMe_whenCredentialsAreValid() {
            // arrange
            val loginId = "seondays"
            val rawPassword = "Password1!"
            val signUpRequest = UserV1Dto.SignUpRequest(
                loginId = loginId,
                password = rawPassword,
                name = "선데이",
                birthDate = LocalDate.of(1990, 1, 1),
                email = "seondays@example.com",
            )
            testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                jsonEntity(signUpRequest),
                object : ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>>() {},
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.GetUserInfoResponse>>() {}
            val headers = HttpHeaders().apply {
                set("X-Loopers-LoginId", loginId)
                set("X-Loopers-LoginPw", rawPassword)
            }
            val response = testRestTemplate.exchange(
                "$ENDPOINT/me",
                HttpMethod.GET,
                HttpEntity<Unit>(headers),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.loginId).isEqualTo(loginId) },
                { assertThat(response.body?.data?.name).isEqualTo("선데*") },
                { assertThat(response.body?.data?.email).isEqualTo("seondays@example.com") },
            )
        }

        @DisplayName("X-Loopers-LoginId 헤더가 없으면 401 Unauthorized 를 반환한다.")
        @Test
        fun getMe_whenLoginIdHeaderIsMissing() {
            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.GetUserInfoResponse>>() {}
            val headers = HttpHeaders().apply { set("X-Loopers-LoginPw", "Password1!") }
            val response = testRestTemplate.exchange(
                "$ENDPOINT/me",
                HttpMethod.GET,
                HttpEntity<Unit>(headers),
                responseType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @DisplayName("X-Loopers-LoginPw 헤더가 없으면 401 Unauthorized 를 반환한다.")
        @Test
        fun getMe_whenPasswordHeaderIsMissing() {
            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.GetUserInfoResponse>>() {}
            val headers = HttpHeaders().apply { set("X-Loopers-LoginId", "seondays") }
            val response = testRestTemplate.exchange(
                "$ENDPOINT/me",
                HttpMethod.GET,
                HttpEntity<Unit>(headers),
                responseType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @DisplayName("비밀번호가 일치하지 않으면 401 Unauthorized 를 반환한다.")
        @Test
        fun getMe_whenPasswordDoesNotMatch() {
            // arrange
            val loginId = "seondays"
            val signUpRequest = UserV1Dto.SignUpRequest(
                loginId = loginId,
                password = "Password1!",
                name = "선데이",
                birthDate = LocalDate.of(1990, 1, 1),
                email = "seondays@example.com",
            )
            testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                jsonEntity(signUpRequest),
                object : ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>>() {},
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.GetUserInfoResponse>>() {}
            val headers = HttpHeaders().apply {
                set("X-Loopers-LoginId", loginId)
                set("X-Loopers-LoginPw", "WrongPass1!")
            }
            val response = testRestTemplate.exchange(
                "$ENDPOINT/me",
                HttpMethod.GET,
                HttpEntity<Unit>(headers),
                responseType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }

    private fun <T> jsonEntity(body: T): HttpEntity<T> {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        return HttpEntity(body, headers)
    }
}
