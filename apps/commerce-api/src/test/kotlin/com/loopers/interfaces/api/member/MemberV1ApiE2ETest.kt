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
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MemberV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val ENDPOINT_REGISTER = "/api/v1/members/register"

        private const val LOGIN_ID = "user01"
        private const val PASSWORD = "Password1!"
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
}