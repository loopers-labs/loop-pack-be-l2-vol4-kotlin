package com.loopers.config.event

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@EnableAsync
@EnableScheduling
@Configuration
class ApplicationEventAsyncConfig {
    @Bean(EVENT_ASYNC_TASK_EXECUTOR)
    fun eventAsyncTaskExecutor(): TaskExecutor {
        return ThreadPoolTaskExecutor().apply {
            corePoolSize = 2
            maxPoolSize = 4
            queueCapacity = 100
            setThreadNamePrefix("event-async-")
            setWaitForTasksToCompleteOnShutdown(true)
            setAwaitTerminationSeconds(10)
            initialize()
        }
    }

    companion object {
        const val EVENT_ASYNC_TASK_EXECUTOR = "eventAsyncTaskExecutor"
    }
}
