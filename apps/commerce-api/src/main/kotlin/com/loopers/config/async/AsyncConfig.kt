package com.loopers.config.async

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor

@EnableAsync
@Configuration
class AsyncConfig {
    @Bean(EVENT_LISTENER_EXECUTOR)
    fun eventListenerExecutor(): Executor {
        return ThreadPoolTaskExecutor().apply {
            corePoolSize = 2
            maxPoolSize = 8
            queueCapacity = 100
            setThreadNamePrefix("like-event-")
            setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
            initialize()
        }
    }

    companion object {
        const val EVENT_LISTENER_EXECUTOR = "eventListenerExecutor"
    }
}
