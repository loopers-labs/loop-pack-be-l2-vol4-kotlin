package com.loopers.batch.job.likecount.step

import com.loopers.batch.job.likecount.LikeCountSyncJobConfig
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@StepScope
@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = LikeCountSyncJobConfig.JOB_NAME)
@Component
class LikeCountSyncTasklet(
    private val jdbcTemplate: JdbcTemplate,
) : Tasklet {
    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        jdbcTemplate.execute(
            """
            INSERT INTO product_like_counts (product_id, brand_id, like_count)
            SELECT p.id, p.brand_id, COALESCE(lc.cnt, 0)
            FROM products p
            LEFT JOIN (
                SELECT product_id, COUNT(*) AS cnt
                FROM likes
                WHERE deleted_at IS NULL
                GROUP BY product_id
            ) lc ON lc.product_id = p.id
            WHERE p.deleted_at IS NULL
            ON DUPLICATE KEY UPDATE
                like_count = VALUES(like_count),
                brand_id = VALUES(brand_id)
            """.trimIndent(),
        )
        return RepeatStatus.FINISHED
    }
}
