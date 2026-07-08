package com.loopers.application.queue

import com.loopers.domain.queue.EntryTokenRepository
import com.loopers.domain.queue.WaitingQueueRepository
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration

@SpringBootTest(properties = ["commerce.queue.scheduler.batch-size=0"])
class WaitingQueueServiceIntegrationTest @Autowired constructor(
    private val waitingQueueService: WaitingQueueService,
    private val waitingQueueRepository: WaitingQueueRepository,
    private val entryTokenRepository: EntryTokenRepository,
    private val redisCleanUp: RedisCleanUp,
) {
    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
    }

    @DisplayName("TTL이 지나면 입장 토큰은 무효화된다")
    @Test
    fun expiresEntryToken() {
        entryTokenRepository.issue(memberId = 1L, token = "token-1", ttl = Duration.ofMillis(300))

        Thread.sleep(500)

        assertThat(entryTokenRepository.find(1L)).isNull()
    }

    @DisplayName("대기 인원이 batchSize보다 많아도 한 번에 batchSize만큼만 토큰을 발급한다")
    @Test
    fun issuesEntryTokensWithinBatchSize() {
        val batchSize = 3L
        val memberIds = (1L..5L).toList()
        memberIds.forEach { memberId ->
            waitingQueueRepository.enterIfAbsent(memberId = memberId, score = memberId.toDouble())
        }

        val issuedTokens = waitingQueueService.issueNextEntries(batchSize)

        assertAll(
            { assertThat(issuedTokens).hasSize(batchSize.toInt()) },
            { assertThat(waitingQueueRepository.count()).isEqualTo(memberIds.size - batchSize) },
            { assertThat(memberIds.take(batchSize.toInt()).map(entryTokenRepository::find)).allMatch { it != null } },
            { assertThat(memberIds.drop(batchSize.toInt()).map(entryTokenRepository::find)).allMatch { it == null } },
        )
    }

    private companion object {
        private val redisContainer = GenericContainer(DockerImageName.parse("redis:latest"))
            .withExposedPorts(REDIS_PORT)
            .apply {
                start()
            }

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
