package com.loopers.interfaces.api

import com.loopers.interfaces.api.queue.WaitingQueueV1Dto
import com.loopers.interfaces.api.user.UserV1Dto
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
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
import java.time.LocalDate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WaitingQueueV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    companion object {
        private const val QUEUE_ENTER_ENDPOINT = "/api/v1/queue/enter"
        private const val QUEUE_POSITION_ENDPOINT = "/api/v1/queue/position"
        private const val USER_ENDPOINT = "/api/v1/users"
        private const val LOGIN_ID = "seondays"
        private const val PASSWORD = "Password1!"
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @DisplayName("POST /api/v1/queue/enter")
    @Nested
    inner class Enter {
        @DisplayName("인증된 사용자가 대기열에 진입하면 200과 함께 순번을 반환한다.")
        @Test
        fun enter_returnsPosition_whenAuthenticated() {
            // arrange
            val headers = signUpAndGetAuthHeaders()

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<WaitingQueueV1Dto.EnterResponse>>() {}
            val response = testRestTemplate.exchange(
                QUEUE_ENTER_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(null, headers),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.rank).isEqualTo(1L) },
                { assertThat(response.body?.data?.totalCount).isEqualTo(1L) },
            )
        }

        @DisplayName("인증 헤더가 없으면 401을 반환한다.")
        @Test
        fun enter_returnsUnauthorized_whenNoAuthHeaders() {
            // arrange
            val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}
            val response = testRestTemplate.exchange(
                QUEUE_ENTER_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(null, headers),
                responseType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }

    @DisplayName("GET /api/v1/queue/position")
    @Nested
    inner class GetPosition {
        @DisplayName("대기열에 진입한 사용자가 조회하면 200과 함께 순번을 반환한다.")
        @Test
        fun getPosition_returnsPosition_whenInQueue() {
            // arrange
            val headers = signUpAndGetAuthHeaders()
            testRestTemplate.exchange(
                QUEUE_ENTER_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(null, headers),
                object : ParameterizedTypeReference<ApiResponse<WaitingQueueV1Dto.EnterResponse>>() {},
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<WaitingQueueV1Dto.PositionResponse>>() {}
            val response = testRestTemplate.exchange(
                QUEUE_POSITION_ENDPOINT,
                HttpMethod.GET,
                HttpEntity(null, headers),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.rank).isEqualTo(1L) },
                { assertThat(response.body?.data?.totalCount).isEqualTo(1L) },
            )
        }

        @DisplayName("대기열에 없는 사용자가 조회하면 404를 반환한다.")
        @Test
        fun getPosition_returnsNotFound_whenNotInQueue() {
            // arrange
            val headers = signUpAndGetAuthHeaders()

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}
            val response = testRestTemplate.exchange(
                QUEUE_POSITION_ENDPOINT,
                HttpMethod.GET,
                HttpEntity(null, headers),
                responseType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("인증 헤더가 없으면 401을 반환한다.")
        @Test
        fun getPosition_returnsUnauthorized_whenNoAuthHeaders() {
            // arrange
            val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}
            val response = testRestTemplate.exchange(
                QUEUE_POSITION_ENDPOINT,
                HttpMethod.GET,
                HttpEntity(null, headers),
                responseType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }

    private fun signUpAndGetAuthHeaders(): HttpHeaders {
        val signUpRequest = UserV1Dto.SignUpRequest(
            loginId = LOGIN_ID,
            password = PASSWORD,
            name = "선데이",
            birthDate = LocalDate.of(1990, 1, 1),
            email = "seondays@example.com",
        )
        val jsonHeaders = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        testRestTemplate.exchange(
            USER_ENDPOINT,
            HttpMethod.POST,
            HttpEntity(signUpRequest, jsonHeaders),
            object : ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>>() {},
        )
        return HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-Loopers-LoginId", LOGIN_ID)
            set("X-Loopers-LoginPw", PASSWORD)
        }
    }
}
