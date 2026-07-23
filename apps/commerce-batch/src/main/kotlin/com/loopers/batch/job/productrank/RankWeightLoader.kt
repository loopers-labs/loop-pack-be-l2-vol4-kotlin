package com.loopers.batch.job.productrank

import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * 활성 가중치 버전을 Job 시작 시 1회 조회한다. 일간 랭킹(collector 적재)과 같은 SoT를 읽어
 * 기간 랭킹의 점수 산식을 일치시킨다. 활성 버전이 없으면 기본 가중치로 폴백한다.
 */
@Component
class RankWeightLoader(
    private val jdbcTemplate: JdbcTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun loadActive(): RankWeights {
        val rows = jdbcTemplate.query(
            """
            SELECT view_weight, like_weight, order_weight
            FROM ranking_weight_config
            WHERE status = 'ACTIVE'
            ORDER BY activated_at DESC
            LIMIT 1
            """.trimIndent(),
        ) { rs, _ ->
            RankWeights(
                viewWeight = rs.getLong("view_weight"),
                likeWeight = rs.getLong("like_weight"),
                orderWeight = rs.getLong("order_weight"),
            )
        }
        return rows.firstOrNull() ?: RankWeights.DEFAULT.also {
            log.warn("활성 가중치 버전이 없어 기본 가중치로 폴백한다. default={}", it)
        }
    }
}
