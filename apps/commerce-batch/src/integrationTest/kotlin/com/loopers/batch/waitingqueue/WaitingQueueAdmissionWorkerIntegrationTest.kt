package com.loopers.batch.waitingqueue

import com.loopers.config.redis.RedisConfig
import com.loopers.config.waitingqueue.WaitingQueueProperties
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import java.time.Duration

@SpringBootTest
class WaitingQueueAdmissionWorkerIntegrationTest @Autowired constructor(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
    private val redisCleanUp: RedisCleanUp,
) {
    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
    }

    @Test
    fun admitMovesWaitingTokensToAllowedKeysAtomically() {
        val worker = WaitingQueueAdmissionWorker(
            redisTemplate,
            WaitingQueueProperties().apply {
                admitCount = 2
                allowedTtl = Duration.ofSeconds(60)
                heartbeatTtl = Duration.ofSeconds(10)
            },
        )
        seedWaitingToken(token = "token-1", userId = 101, sequence = 1.0)
        seedWaitingToken(token = "token-2", userId = 102, sequence = 2.0)
        seedWaitingToken(token = "token-3", userId = 103, sequence = 3.0)

        val admitted = worker.admit()

        assertAll(
            { assertThat(admitted).isEqualTo(2) },
            { assertThat(redisTemplate.opsForValue().get("wq:orders:allowed:token-1")).isEqualTo("101") },
            { assertThat(redisTemplate.opsForValue().get("wq:orders:allowed:token-2")).isEqualTo("102") },
            { assertThat(redisTemplate.opsForValue().get("wq:orders:allowed:token-3")).isNull() },
            { assertThat(redisTemplate.opsForHash<String, String>().entries("wq:orders:token:token-1")).isEmpty() },
            { assertThat(redisTemplate.opsForValue().get("wq:orders:user:101")).isNull() },
            { assertThat(redisTemplate.opsForZSet().range("wq:orders:queue", 0, -1)).containsExactly("token-3") },
            { assertThat(redisTemplate.opsForValue().get("wq:orders:heartbeat")).isNotNull() },
        )
    }

    private fun seedWaitingToken(token: String, userId: Long, sequence: Double) {
        redisTemplate.opsForZSet().add("wq:orders:queue", token, sequence)
        redisTemplate.opsForHash<String, String>().put("wq:orders:token:$token", "userId", userId.toString())
        redisTemplate.opsForValue().set("wq:orders:user:$userId", token)
    }
}
