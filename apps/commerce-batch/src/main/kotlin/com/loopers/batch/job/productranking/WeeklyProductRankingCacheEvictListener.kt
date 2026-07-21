package com.loopers.batch.job.productranking

import com.loopers.config.redis.RankingRedisKeys
import com.loopers.config.redis.RedisConfig
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobExecutionListener
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate

class WeeklyProductRankingCacheEvictListener(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
) : JobExecutionListener {
    override fun afterJob(jobExecution: JobExecution) {
        if (jobExecution.status != BatchStatus.COMPLETED) {
            return
        }
        val baseDate = jobExecution.jobParameters.getLocalDate(ProductRankingJobParametersValidator.BASE_DATE_PARAMETER)
            ?: return
        redisTemplate.delete(RankingRedisKeys.weekly(baseDate))
    }
}
