package com.loopers.testcontainers

import com.redis.testcontainers.RedisContainer
import org.springframework.context.annotation.Configuration

@Configuration
class RedisTestContainersConfig {
    companion object {
        private val redisContainer = RedisContainer("redis:latest")
            .apply {
                start()
            }

        // 시스템 프로퍼티는 RedisProperties 바인딩보다 먼저 적용되어야 하므로 static init 에서 설정한다.
        // (인스턴스 init 은 빈 생성 시점이라 바인딩보다 늦어 컨테이너 포트가 반영되지 않는다.)
        init {
            System.setProperty("datasource.redis.database", "0")
            System.setProperty("datasource.redis.master.host", redisContainer.host)
            System.setProperty("datasource.redis.master.port", redisContainer.firstMappedPort.toString())
            System.setProperty("datasource.redis.replicas[0].host", redisContainer.host)
            System.setProperty("datasource.redis.replicas[0].port", redisContainer.firstMappedPort.toString())
        }
    }
}
