package com.loopers.interfaces.api.queue

import com.loopers.domain.user.PasswordEncoder
import com.loopers.infrastructure.member.entity.MemberEntity
import com.loopers.infrastructure.member.repository.MemberJpaRepository
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.queue.dto.QueueV1Dto
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
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.time.LocalDate
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QueueV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val memberJpaRepository: MemberJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @DisplayName("POST /api/v1/queue/enter")
    @Nested
    inner class Enter {
        @DisplayName("대기열에 처음 진입하면 0-based rank와 전체 대기 인원을 반환한다")
        @Test
        fun entersWaitingQueue() {
            createMember()

            val response = enter(loginId = LOGIN_ID)

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.rank).isEqualTo(0L) },
                { assertThat(response.body?.data?.totalWaiting).isEqualTo(1L) },
                { assertThat(response.body?.data?.entryToken).isNull() },
            )
        }

        @DisplayName("같은 사용자가 반복 진입해도 기존 순번을 유지한다")
        @Test
        fun keepsRank_whenSameUserEntersRepeatedly() {
            createMember()

            val firstResponse = enter(loginId = LOGIN_ID)
            createMember(loginId = "other123")
            enter(loginId = "other123")
            val secondResponse = enter(loginId = LOGIN_ID)

            assertAll(
                { assertThat(firstResponse.body?.data?.rank).isEqualTo(0L) },
                { assertThat(secondResponse.body?.data?.rank).isEqualTo(0L) },
                { assertThat(secondResponse.body?.data?.totalWaiting).isEqualTo(2L) },
            )
        }

        @DisplayName("여러 사용자가 동시에 진입해도 각 사용자에게 고유한 순번을 부여한다")
        @Test
        fun assignsUniqueRanks_whenUsersEnterConcurrently() {
            val members = (1..CONCURRENT_USER_COUNT).map { index ->
                createMember(loginId = "loopers$index")
            }
            val executor = Executors.newFixedThreadPool(CONCURRENT_USER_COUNT)
            val ready = CountDownLatch(CONCURRENT_USER_COUNT)
            val start = CountDownLatch(1)

            val futures = members.map { member ->
                executor.submit(
                    Callable {
                        ready.countDown()
                        start.await(5, TimeUnit.SECONDS)
                        enter(loginId = member.loginId)
                    },
                )
            }
            ready.await(5, TimeUnit.SECONDS)
            start.countDown()
            val responses = futures.map { it.get(10, TimeUnit.SECONDS) }
            executor.shutdown()

            val ranks = responses.mapNotNull { it.body?.data?.rank }
            assertAll(
                { assertThat(responses).allMatch { it.statusCode == HttpStatus.OK } },
                { assertThat(ranks).containsExactlyInAnyOrderElementsOf((0L until CONCURRENT_USER_COUNT.toLong()).toList()) },
                { assertThat(responses.mapNotNull { it.body?.data?.totalWaiting }.toSet()).contains(CONCURRENT_USER_COUNT.toLong()) },
            )
        }
    }

    private fun enter(
        loginId: String,
        password: String = RAW_PASSWORD,
    ): org.springframework.http.ResponseEntity<ApiResponse<QueueV1Dto.PositionResponse>> {
        return testRestTemplate.exchange(
            "$QUEUE_ENDPOINT/enter",
            HttpMethod.POST,
            HttpEntity<Unit>(createAuthHeaders(loginId = loginId, password = password)),
            object : ParameterizedTypeReference<ApiResponse<QueueV1Dto.PositionResponse>>() {},
        )
    }

    private fun createMember(
        loginId: String = LOGIN_ID,
        password: String = RAW_PASSWORD,
    ): MemberEntity {
        return memberJpaRepository.save(
            MemberEntity(
                loginId = loginId,
                password = PasswordEncoder.encode(password),
                name = "홍길동",
                birthDate = LocalDate.of(1990, 1, 1),
                email = "$loginId@example.com",
            ),
        )
    }

    private fun createAuthHeaders(
        loginId: String,
        password: String,
    ): HttpHeaders {
        return HttpHeaders().apply {
            set("X-Loopers-LoginId", loginId)
            set("X-Loopers-LoginPw", password)
        }
    }

    private companion object {
        private val redisContainer = GenericContainer(DockerImageName.parse("redis:latest"))
            .withExposedPorts(REDIS_PORT)
            .apply {
                start()
            }

        private const val QUEUE_ENDPOINT = "/api/v1/queue"
        private const val LOGIN_ID = "loopers123"
        private const val RAW_PASSWORD = "Loopers123!"
        private const val CONCURRENT_USER_COUNT = 8

        @JvmStatic
        @DynamicPropertySource
        fun redisProperties(registry: DynamicPropertyRegistry) {
            registry.add("datasource.redis.database") { "0" }
            registry.add("datasource.redis.master.host") { redisContainer.host }
            registry.add("datasource.redis.master.port") { redisContainer.getMappedPort(REDIS_PORT).toString() }
            registry.add("datasource.redis.replicas[0].host") { redisContainer.host }
            registry.add("datasource.redis.replicas[0].port") { redisContainer.getMappedPort(REDIS_PORT).toString() }
        }

        private const val REDIS_PORT = 6379
    }
}
