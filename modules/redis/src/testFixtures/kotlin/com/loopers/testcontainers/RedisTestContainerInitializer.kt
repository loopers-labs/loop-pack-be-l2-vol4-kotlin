package com.loopers.testcontainers

import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.MapPropertySource

class RedisTestContainerInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        val redis = RedisTestContainersConfig.redisContainer
        applicationContext.environment.propertySources.addFirst(
            MapPropertySource(
                "redisTestContainer",
                mapOf(
                    "datasource.redis.database" to 0,
                    "datasource.redis.master.host" to redis.host,
                    "datasource.redis.master.port" to redis.firstMappedPort,
                    "datasource.redis.replicas[0].host" to redis.host,
                    "datasource.redis.replicas[0].port" to redis.firstMappedPort,
                ),
            ),
        )
    }
}
