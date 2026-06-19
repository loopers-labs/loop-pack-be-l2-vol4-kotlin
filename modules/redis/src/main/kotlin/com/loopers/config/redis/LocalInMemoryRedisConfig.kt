package com.loopers.config.redis

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate

@Configuration
@Profile("local")
class LocalInMemoryRedisConfig {
    private val store = InMemoryRedisStore()

    @Bean
    fun redisConnectionFactory(): RedisConnectionFactory =
        InMemoryRedisConnectionFactory(store)

    @Primary
    @Bean
    fun defaultRedisTemplate(): RedisTemplate<String, String> =
        InMemoryRedisTemplate(store)

    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    @Bean
    fun masterRedisTemplate(): RedisTemplate<String, String> =
        InMemoryRedisTemplate(store)
}
