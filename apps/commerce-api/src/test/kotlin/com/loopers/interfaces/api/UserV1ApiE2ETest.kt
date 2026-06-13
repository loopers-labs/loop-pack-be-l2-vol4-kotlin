package com.loopers.interfaces.api

import com.loopers.interfaces.api.user.UserV1Dto
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
import org.springframework.http.ResponseEntity

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val ENDPOINT = "/api/v1/users"
        private val VALID_REQUEST = UserV1Dto.RegisterRequest(
            loginId = "testUser01",
            password = "Pass!234",
            name = "홍길동",
            birthDate = "19900101",
            email = "test@example.com",
        )
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("POST /api/v1/users")
    @Nested
    inner class Register {
        @DisplayName("유효한 정보로 요청하면, 201 Created 와 유저 정보를 반환한다.")
        @Test
        fun returnsCreated_whenValidInfoIsProvided() {
            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.RegisterResponse>>() {}
            val response = testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, HttpEntity(VALID_REQUEST), responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
                { assertThat(response.body?.data?.id).isGreaterThan(0) },
                { assertThat(response.body?.data?.loginId).isEqualTo(VALID_REQUEST.loginId) },
                { assertThat(response.body?.data?.name).isEqualTo(VALID_REQUEST.name) },
                { assertThat(response.body?.data?.email).isEqualTo(VALID_REQUEST.email) },
            )
        }

        @DisplayName("이미 존재하는 로그인 ID로 요청하면, 409 Conflict 를 반환한다.")
        @Test
        fun returnsConflict_whenLoginIdAlreadyExists() {
            // arrange
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.RegisterResponse>>() {}
            testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, HttpEntity(VALID_REQUEST), responseType)

            // act
            val response = testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, HttpEntity(VALID_REQUEST), responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }

        @DisplayName("이메일 형식이 올바르지 않으면, 400 Bad Request 를 반환한다.")
        @Test
        fun returnsBadRequest_whenEmailIsInvalid() {
            // arrange
            val request = VALID_REQUEST.copy(email = "invalid-email")

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.RegisterResponse>>() {}
            val response = testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, HttpEntity(request), responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }

        @DisplayName("비밀번호에 생년월일이 포함되면, 400 Bad Request 를 반환한다.")
        @Test
        fun returnsBadRequest_whenPasswordContainsBirthDate() {
            // arrange
            val request = VALID_REQUEST.copy(password = "Ab!19900101")

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.RegisterResponse>>() {}
            val response = testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, HttpEntity(request), responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }

        @DisplayName("비밀번호가 8자 미만이면, 400 Bad Request 를 반환한다.")
        @Test
        fun returnsBadRequest_whenPasswordIsTooShort() {
            // arrange
            val request = VALID_REQUEST.copy(password = "Ab1!567")

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.RegisterResponse>>() {}
            val response = testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, HttpEntity(request), responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }

        @DisplayName("생년월일이 yyyyMMdd 형식이 아니면, 400 Bad Request 를 반환한다.")
        @Test
        fun returnsBadRequest_whenBirthDateIsInvalidFormat() {
            // arrange
            val request = VALID_REQUEST.copy(birthDate = "1990-01-01")

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.RegisterResponse>>() {}
            val response = testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, HttpEntity(request), responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }
    }

    @DisplayName("GET /api/v1/users/me")
    @Nested
    inner class GetMe {
        private fun authHeaders(loginId: String = VALID_REQUEST.loginId, password: String = VALID_REQUEST.password) =
            HttpHeaders().apply {
                set("X-Loopers-LoginId", loginId)
                set("X-Loopers-LoginPw", password)
            }

        private fun getMe(loginId: String = VALID_REQUEST.loginId, password: String = VALID_REQUEST.password): ResponseEntity<ApiResponse<UserV1Dto.MeResponse>> {
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.MeResponse>>() {}
            return testRestTemplate.exchange("$ENDPOINT/me", HttpMethod.GET, HttpEntity<Unit>(authHeaders(loginId, password)), responseType)
        }

        @DisplayName("유효한 헤더로 요청하면, 200 OK 와 마스킹된 이름을 포함한 유저 정보를 반환한다.")
        @Test
        fun returnsOk_whenValidHeadersAreProvided() {
            // arrange
            testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, HttpEntity(VALID_REQUEST), object : ParameterizedTypeReference<ApiResponse<UserV1Dto.RegisterResponse>>() {})

            // act
            val response = getMe()

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
                { assertThat(response.body?.data?.loginId).isEqualTo(VALID_REQUEST.loginId) },
                { assertThat(response.body?.data?.name).isEqualTo("홍길*") },
                { assertThat(response.body?.data?.birthDate).isEqualTo(VALID_REQUEST.birthDate) },
                { assertThat(response.body?.data?.email).isEqualTo(VALID_REQUEST.email) },
            )
        }

        @DisplayName("존재하지 않는 loginId 로 요청하면, 404 Not Found 를 반환한다.")
        @Test
        fun returnsNotFound_whenLoginIdDoesNotExist() {
            // act
            val response = getMe(loginId = "nonexistent")

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }

        @DisplayName("비밀번호가 일치하지 않으면, 400 Bad Request 를 반환한다.")
        @Test
        fun returnsBadRequest_whenPasswordDoesNotMatch() {
            // arrange
            testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, HttpEntity(VALID_REQUEST), object : ParameterizedTypeReference<ApiResponse<UserV1Dto.RegisterResponse>>() {})

            // act
            val response = getMe(password = "WrongPass!1")

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }
    }

    @DisplayName("PATCH /api/v1/users/me/password")
    @Nested
    inner class ChangePassword {
        private fun register() {
            testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, HttpEntity(VALID_REQUEST), object : ParameterizedTypeReference<ApiResponse<UserV1Dto.RegisterResponse>>() {})
        }

        private fun changePassword(
            loginId: String = VALID_REQUEST.loginId,
            password: String = VALID_REQUEST.password,
            request: UserV1Dto.ChangePasswordRequest,
        ): ResponseEntity<ApiResponse<Unit>> {
            val headers = HttpHeaders().apply {
                set("X-Loopers-LoginId", loginId)
                set("X-Loopers-LoginPw", password)
            }
            val responseType = object : ParameterizedTypeReference<ApiResponse<Unit>>() {}
            return testRestTemplate.exchange("$ENDPOINT/me/password", HttpMethod.PATCH, HttpEntity(request, headers), responseType)
        }

        @DisplayName("유효한 헤더와 새 비밀번호로 요청하면, 200 OK 를 반환한다.")
        @Test
        fun returnsOk_whenValidHeadersAndNewPasswordAreProvided() {
            // arrange
            register()
            val request = UserV1Dto.ChangePasswordRequest(currentPassword = "Pass!234", newPassword = "NewPass!9")

            // act
            val response = changePassword(request = request)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
            )
        }

        @DisplayName("존재하지 않는 loginId 로 요청하면, 404 Not Found 를 반환한다.")
        @Test
        fun returnsNotFound_whenLoginIdDoesNotExist() {
            // arrange
            val request = UserV1Dto.ChangePasswordRequest(currentPassword = "Pass!234", newPassword = "NewPass!9")

            // act
            val response = changePassword(loginId = "nonexistent", request = request)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }

        @DisplayName("현재 비밀번호가 일치하지 않으면, 400 Bad Request 를 반환한다.")
        @Test
        fun returnsBadRequest_whenCurrentPasswordDoesNotMatch() {
            // arrange
            register()
            val request = UserV1Dto.ChangePasswordRequest(currentPassword = "WrongPass!1", newPassword = "NewPass!9")

            // act
            val response = changePassword(request = request)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 동일하면, 400 Bad Request 를 반환한다.")
        @Test
        fun returnsBadRequest_whenNewPasswordIsSameAsCurrentPassword() {
            // arrange
            register()
            val request = UserV1Dto.ChangePasswordRequest(currentPassword = "Pass!234", newPassword = "Pass!234")

            // act
            val response = changePassword(request = request)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }

        @DisplayName("새 비밀번호에 생년월일이 포함되면, 400 Bad Request 를 반환한다.")
        @Test
        fun returnsBadRequest_whenNewPasswordContainsBirthDate() {
            // arrange
            register()
            val request = UserV1Dto.ChangePasswordRequest(currentPassword = "Pass!234", newPassword = "Ab!19900101")

            // act
            val response = changePassword(request = request)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }
    }
}
