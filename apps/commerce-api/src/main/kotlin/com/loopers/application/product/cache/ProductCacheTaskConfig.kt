package com.loopers.application.product.cache

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration
class ProductCacheTaskConfig {
    @Bean(PRODUCT_CACHE_TASK_EXECUTOR)
    fun productCacheTaskExecutor(): TaskExecutor {
        return ThreadPoolTaskExecutor().apply {
            corePoolSize = 2
            maxPoolSize = 4
            queueCapacity = 100
            setThreadNamePrefix("product-cache-")
            initialize()
        }
    }

    companion object {
        const val PRODUCT_CACHE_TASK_EXECUTOR = "productCacheTaskExecutor"
    }
}
