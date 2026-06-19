package com.loopers.config.redis

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("local")
@SpringBootTest(classes = [LocalInMemoryRedisConfig::class])
class LocalInMemoryRedisConfigTest @Autowired constructor(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
) {
    @Test
    fun localProfileUsesInMemoryRedisTemplate() {
        redisTemplate.opsForValue().set("catalog:product:display:1", "cached")
        redisTemplate.opsForSet().add("catalog:brand-products:1", "1")

        assertThat(redisTemplate).isInstanceOf(InMemoryRedisTemplate::class.java)
        assertThat(redisTemplate.opsForValue().get("catalog:product:display:1")).isEqualTo("cached")
        assertThat(redisTemplate.opsForSet().members("catalog:brand-products:1")).containsExactly("1")
    }
}
