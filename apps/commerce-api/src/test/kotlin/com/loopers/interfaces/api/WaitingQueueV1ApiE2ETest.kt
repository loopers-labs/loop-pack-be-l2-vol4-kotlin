package com.loopers.interfaces.api

import com.loopers.application.user.SignupCommand
import com.loopers.interfaces.api.user.UserApplicationServicePort
import com.loopers.interfaces.api.waitingqueue.QueueProtectedTestController
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
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
import org.springframework.http.ResponseEntity
import java.time.LocalDate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WaitingQueueV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val userApplicationService: UserApplicationServicePort,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    private fun signup(loginId: String = "tester01", pw: String = "password1234") {
        userApplicationService.signup(
            SignupCommand(
                loginId = loginId,
                rawPassword = pw,
                name = "테스터",
                birth = LocalDate.of(2000, 1, 1),
                email = "$loginId@example.com",
            ),
        )
    }

    private fun authHeaders(loginId: String?, loginPw: String?): HttpHeaders = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
        loginId?.let { set("X-Loopers-LoginId", it) }
        loginPw?.let { set("X-Loopers-LoginPw", it) }
    }

    private fun callProtected(loginId: String?, loginPw: String?): ResponseEntity<ApiResponse<Map<String, Any?>>> {
        val responseType = object : ParameterizedTypeReference<ApiResponse<Map<String, Any?>>>() {}
        return testRestTemplate.exchange(
            QueueProtectedTestController.PATH,
            HttpMethod.GET,
            HttpEntity<Any>(authHeaders(loginId, loginPw)),
            responseType,
        )
    }

    @DisplayName("@WaitingQueue 보호 API 를 입장 토큰 없이 호출하면, 429 와 대기열 토큰을 받는다.")
    @Test
    fun returnsTooManyRequestsWithWaitToken() {
        signup()

        val response = callProtected("tester01", "password1234")

        assertAll(
            { assertThat(response.statusCode).isEqualTo(HttpStatus.TOO_MANY_REQUESTS) },
            { assertThat(response.body?.data?.get("topic")).isEqualTo("order") },
            { assertThat(response.body?.data?.get("waitToken") as? String).startsWith("wq.") },
        )
    }

    @DisplayName("보호 API 를 다시 호출하면, 매번 새 대기열 토큰이 발급된다(맨 뒤 재진입).")
    @Test
    fun reissuesWaitTokenOnEachCall() {
        signup()

        val first = callProtected("tester01", "password1234")
        val second = callProtected("tester01", "password1234")

        assertAll(
            { assertThat(first.statusCode).isEqualTo(HttpStatus.TOO_MANY_REQUESTS) },
            { assertThat(second.statusCode).isEqualTo(HttpStatus.TOO_MANY_REQUESTS) },
            { assertThat(second.body?.data?.get("waitToken") as? String).startsWith("wq.") },
        )
    }

    @DisplayName("인증 헤더 없이 보호 API 를 호출하면, 401 응답을 받는다.")
    @Test
    fun returnsUnauthorizedWithoutAuth() {
        val response = callProtected(null, null)

        assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
    }
}
