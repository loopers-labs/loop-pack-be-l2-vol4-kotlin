package com.loopers.config.redis

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * Redis 장애 대응 요구사항: Redis가 응답하지 않고 지연(hang)되는 장애에서도
 * 요청 스레드가 오래 묶이지 않도록 command timeout으로 빠르게 실패시켜 상위 요청을 fail-closed 한다.
 */
class RedisConfigCommandTimeoutTest {
    @Test
    fun `Redis_명령은_기본_1초_command_timeout으로_제한된다`() {
        val config = RedisConfig(redisProperties())

        assertThat(config.defaultRedisConnectionFactory().timeout).isEqualTo(1_000L)
        assertThat(config.masterRedisConnectionFactory().timeout).isEqualTo(1_000L)
    }

    @Test
    fun `Redis_command_timeout은_datasource_redis_설정으로_조정할_수_있다`() {
        val config = RedisConfig(redisProperties(commandTimeout = Duration.ofMillis(300)))

        assertThat(config.defaultRedisConnectionFactory().timeout).isEqualTo(300L)
        assertThat(config.masterRedisConnectionFactory().timeout).isEqualTo(300L)
    }

    private fun redisProperties(commandTimeout: Duration? = null): RedisProperties {
        val master = RedisNodeInfo("localhost", 6379)
        return if (commandTimeout == null) {
            RedisProperties(database = 0, master = master, replicas = emptyList())
        } else {
            RedisProperties(database = 0, master = master, replicas = emptyList(), commandTimeout = commandTimeout)
        }
    }
}
