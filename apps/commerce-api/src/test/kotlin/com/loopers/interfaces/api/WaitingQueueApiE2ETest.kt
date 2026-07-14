package com.loopers.interfaces.api

import com.loopers.ApiTest
import com.loopers.domain.user.application.service.UserService
import com.loopers.domain.user.support.UserSteps.Companion.기본_로그인_ID
import com.loopers.domain.user.support.UserSteps.Companion.기본_비밀번호
import com.loopers.domain.user.support.UserSteps.Companion.사용자_회원가입
import com.loopers.domain.waitingqueue.application.WaitingQueueFacade
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource

@TestPropertySource(
    properties = [
        "commerce.waiting-queue.token-ttl=PT5M",
        "commerce.waiting-queue.scheduler-delay=PT10S",
        "commerce.waiting-queue.admission-batch-size=1",
        "commerce.waiting-queue.scheduler-jitter-max=PT0S",
        "commerce.waiting-queue.polling-interval=PT2S",
        "commerce.waiting-queue.redis-key-prefix=waiting-queue",
    ],
)
class WaitingQueueApiE2ETest
    @Autowired
    constructor(
        private val userService: UserService,
        private val waitingQueueFacade: WaitingQueueFacade,
    ) : ApiTest() {
        private val mapResponseType =
            object : ParameterizedTypeReference<ApiResponse<Map<String, Any?>>>() {}

        @Test
        fun `대기열_진입은_WAITING_상태와_1_based_순번을_반환한다`() {
            userService.signUp(사용자_회원가입())

            val response = enterQueue()

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body?.data?.get("status")).isEqualTo("WAITING")
            assertThat(response.body?.data?.number("position")).isEqualTo(1L)
            assertThat(response.body?.data?.number("totalWaiting")).isEqualTo(1L)
            assertThat(response.body?.data?.get("token")).isNull()
        }

        @Test
        fun `반복_진입은_중복_등록하지_않고_같은_순번을_반환한다`() {
            userService.signUp(사용자_회원가입())
            val first = enterQueue()

            val second = enterQueue()

            assertThat(second.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(second.body?.data?.get("status")).isEqualTo("WAITING")
            assertThat(second.body?.data?.number("position")).isEqualTo(first.body?.data?.number("position"))
            assertThat(second.body?.data?.number("totalWaiting")).isEqualTo(1L)
        }

        @Test
        fun `입장_전_순번_조회는_토큰_없이_현재_순번과_polling_주기를_반환한다`() {
            userService.signUp(사용자_회원가입())
            enterQueue()

            val response = position()

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body?.data?.get("status")).isEqualTo("WAITING")
            assertThat(response.body?.data?.number("position")).isEqualTo(1L)
            assertThat(response.body?.data?.number("recommendedPollingIntervalSeconds")).isEqualTo(2L)
            assertThat(response.body?.data?.get("token")).isNull()
        }

        @Test
        fun `대기열_진입_전_순번_조회는_NOT_FOUND와_도메인_메시지를_반환한다`() {
            userService.signUp(사용자_회원가입())

            val response = position()

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
            assertThat(response.body?.meta?.errorCode).isEqualTo(ErrorType.NOT_FOUND.code)
            assertThat(response.body?.meta?.message).isEqualTo("대기열에서 사용자를 찾을 수 없습니다.")
            assertThat(response.body?.data).isNull()
        }

        @Test
        fun `입장_후_순번_조회는_ADMITTED_상태와_토큰_만료시각을_반환한다`() {
            val user = userService.signUp(사용자_회원가입())
            enterQueue()
            waitingQueueFacade.admitBatch()

            val response = position()

            assertThat(user.id).isPositive()
            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body?.data?.get("status")).isEqualTo("ADMITTED")
            assertThat(response.body?.data?.get("position")).isNull()
            assertThat(response.body?.data?.get("token")).isNotNull()
            assertThat(response.body?.data?.get("tokenAvailableAt")).isNotNull()
            assertThat(response.body?.data?.get("tokenExpiresAt")).isNotNull()
        }

        private fun enterQueue() =
            testRestTemplate.exchange(
                "/queue/enter",
                HttpMethod.POST,
                HttpEntity<Unit>(authHeaders()),
                mapResponseType,
            )

        private fun position() =
            testRestTemplate.exchange(
                "/queue/position",
                HttpMethod.GET,
                HttpEntity<Unit>(authHeaders()),
                mapResponseType,
            )

        private fun authHeaders(): HttpHeaders {
            val headers = HttpHeaders()
            headers.set("X-Loopers-LoginId", 기본_로그인_ID)
            headers.set("X-Loopers-LoginPw", 기본_비밀번호)
            return headers
        }

        private fun Map<String, Any?>.number(key: String): Long =
            (get(key) as Number).toLong()
    }
