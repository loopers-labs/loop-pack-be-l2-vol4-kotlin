package com.loopers.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.product.interfaces.ProductListResponse
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import java.time.Duration

@Configuration
@EnableCaching
class CacheConfig {
    @Bean
    fun redisCacheConfiguration(objectMapper: ObjectMapper): RedisCacheConfiguration {
        val serializer = Jackson2JsonRedisSerializer(objectMapper, ProductListResponse::class.java)
        return RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofSeconds(5))
            .disableCachingNullValues()
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
    }
}
