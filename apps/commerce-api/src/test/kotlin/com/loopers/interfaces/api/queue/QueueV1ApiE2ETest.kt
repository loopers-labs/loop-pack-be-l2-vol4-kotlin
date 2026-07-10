package com.loopers.interfaces.api.queue

import com.loopers.application.queue.QueueStatus
import com.loopers.application.user.UserFacade
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.scheduler.queue.QueueAdmissionScheduler
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
import java.time.LocalDate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QueueV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val userFacade: UserFacade,
    private val queueAdmissionScheduler: QueueAdmissionScheduler,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    private val loginId = "user123"
    private val rawPassword = "Valid1!pw"
    private val responseType = object : ParameterizedTypeReference<ApiResponse<QueueV1Dto.QueueResponse>>() {}

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    private fun signUp() {
        userFacade.signUp(loginId, rawPassword, "홍길동", LocalDate.of(1994, 7, 14), "hong@example.com")
    }

    private fun authHeaders(id: String = loginId, pw: String = rawPassword) = HttpHeaders().apply {
        set("X-Loopers-LoginId", id)
        set("X-Loopers-LoginPw", pw)
    }

    private fun enter() = testRestTemplate.exchange(
        "/api/v1/queue/enter",
        HttpMethod.POST,
        HttpEntity<Any>(authHeaders()),
        responseType,
    )

    private fun position() = testRestTemplate.exchange(
        "/api/v1/queue/position",
        HttpMethod.GET,
        HttpEntity<Any>(authHeaders()),
        responseType,
    )

    @DisplayName("POST /api/v1/queue/enter")
    @Nested
    inner class Enter {
        @DisplayName("대기열에 진입하면, WAITING 상태로 순번 1과 전체 대기 인원 1을 반환한다.")
        @Test
        fun entersAndReturnsPosition() {
            // arrange
            signUp()

            // act
            val response = enter()

            // assert
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.status).isEqualTo(QueueStatus.WAITING) },
                { assertThat(response.body?.data?.position).isEqualTo(1L) },
                { assertThat(response.body?.data?.totalWaiting).isEqualTo(1L) },
            )
        }

        @DisplayName("같은 유저가 재진입해도 순번이 유지되고 대기 인원은 늘지 않는다. (ZADD NX)")
        @Test
        fun reEnterKeepsPosition() {
            // arrange
            signUp()
            enter()

            // act
            val response = enter()

            // assert
            assertAll(
                { assertThat(response.body?.data?.position).isEqualTo(1L) },
                { assertThat(response.body?.data?.totalWaiting).isEqualTo(1L) },
            )
        }

        @DisplayName("인증에 실패하면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        fun returnsUnauthorized() {
            // arrange
            signUp()

            // act
            val response = testRestTemplate.exchange(
                "/api/v1/queue/enter",
                HttpMethod.POST,
                HttpEntity<Any>(authHeaders(pw = "WrongPw1!")),
                responseType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }

    @DisplayName("GET /api/v1/queue/position")
    @Nested
    inner class Position {
        @DisplayName("진입한 뒤 조회하면, WAITING 상태로 현재 순번·전체 인원·예상 대기 시간을 반환한다.")
        @Test
        fun returnsPositionAfterEnter() {
            // arrange
            signUp()
            enter()

            // act
            val response = position()

            // assert
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.status).isEqualTo(QueueStatus.WAITING) },
                { assertThat(response.body?.data?.position).isEqualTo(1L) },
                { assertThat(response.body?.data?.totalWaiting).isEqualTo(1L) },
                // 맨 앞(rank 0)이라 예상 대기 0초
                { assertThat(response.body?.data?.estimatedWaitSeconds).isEqualTo(0L) },
            )
        }

        @DisplayName("입장 처리(토큰 발급)된 뒤 조회하면, READY 상태로 입장 토큰을 반환한다.")
        @Test
        fun returnsReadyWhenAdmitted() {
            // arrange
            signUp()
            enter()
            queueAdmissionScheduler.admitOnce() // 스케줄러 로직으로 입장 처리 (ZPOPMIN + 토큰 발급)

            // act
            val response = position()

            // assert
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.status).isEqualTo(QueueStatus.READY) },
                { assertThat(response.body?.data?.token).isNotBlank() },
                { assertThat(response.body?.data?.position).isNull() },
            )
        }

        @DisplayName("진입하지 않은 유저가 순번을 조회하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFoundWhenNotEntered() {
            // arrange
            signUp()

            // act
            val response = position()

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }
}
