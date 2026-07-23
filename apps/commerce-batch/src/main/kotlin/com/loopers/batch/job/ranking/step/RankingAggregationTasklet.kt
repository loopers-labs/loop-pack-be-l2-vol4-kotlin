package com.loopers.batch.job.ranking.step

import com.loopers.domain.metrics.ProductMetricsModel
import com.loopers.domain.ranking.PeriodType
import com.loopers.domain.ranking.ProductRankModel
import com.loopers.infrastructure.ranking.ProductRankRepository
import jakarta.persistence.EntityManagerFactory
import org.slf4j.LoggerFactory
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * 랭킹 집계 Tasklet.
 * product_metrics 전체를 읽어 점수를 계산하고 TOP 100을 MV에 적재한다.
 *
 * 내부적으로 Chunk-Oriented 방식의 로직을 구현:
 * - EntityManager scroll로 대량 데이터를 청크 단위 처리
 * - 점수 계산 후 정렬, TOP 100만 Writer로 저장
 */
@StepScope
@Component
class RankingAggregationTasklet(
    private val entityManagerFactory: EntityManagerFactory,
    private val productRankRepository: ProductRankRepository,
    @Value("#{jobParameters['periodType']}") private val periodTypeParam: String?,
    @Value("#{jobParameters['periodKey']}") private val periodKeyParam: String?,
) : Tasklet {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        val periodType = PeriodType.valueOf(
            periodTypeParam ?: throw IllegalArgumentException("jobParameter 'periodType'는 필수입니다."),
        )
        val periodKey = periodKeyParam
            ?: throw IllegalArgumentException("jobParameter 'periodKey'는 필수입니다.")

        log.info("랭킹 집계 시작: periodType={}, periodKey={}", periodType, periodKey)

        val scoredProducts = readAndScore()
        val top100 = scoredProducts
            .sortedByDescending { it.score }
            .take(TOP_N)

        write(periodType, periodKey, top100)

        log.info("랭킹 집계 완료: {}건 적재", top100.size)
        return RepeatStatus.FINISHED
    }

    /**
     * Chunk 방식으로 product_metrics를 읽고 점수를 계산한다.
     */
    private fun readAndScore(): List<ScoredProduct> {
        val em = entityManagerFactory.createEntityManager()
        val result = mutableListOf<ScoredProduct>()

        try {
            val query = em.createQuery(
                "SELECT m FROM ProductMetricsModel m",
                ProductMetricsModel::class.java,
            )
            query.setHint("org.hibernate.fetchSize", CHUNK_SIZE)

            val metrics = query.resultList

            for (chunk in metrics.chunked(CHUNK_SIZE)) {
                for (metric in chunk) {
                    val score = calculateScore(metric)
                    if (score > 0) {
                        result.add(
                            ScoredProduct(
                                productId = metric.productId,
                                score = score,
                                viewCount = metric.viewCount,
                                likeCount = metric.likeCount,
                                orderCount = metric.orderCount,
                                salesAmount = metric.salesAmount,
                            ),
                        )
                    }
                }
                em.clear()
            }
        } finally {
            em.close()
        }

        return result
    }

    /**
     * 기존 데이터 삭제 후 TOP 100 적재.
     */
    private fun write(periodType: PeriodType, periodKey: String, ranked: List<ScoredProduct>) {
        productRankRepository.deleteByPeriodTypeAndPeriodKey(periodType, periodKey)
        productRankRepository.flush()

        val entities = ranked.mapIndexed { index, item ->
            ProductRankModel(
                periodType = periodType,
                periodKey = periodKey,
                ranking = index + 1,
                productId = item.productId,
                score = item.score,
                viewCount = item.viewCount,
                likeCount = item.likeCount,
                orderCount = item.orderCount,
                salesAmount = item.salesAmount,
            )
        }

        productRankRepository.saveAll(entities)
    }

    /**
     * 랭킹 점수 계산 (RankingService와 동일한 가중치 적용).
     * view: 0.1, like: 0.2, order: 0.7 (log 정규화)
     */
    private fun calculateScore(metric: ProductMetricsModel): Double {
        val viewScore = WEIGHT_VIEW * metric.viewCount
        val likeScore = WEIGHT_LIKE * metric.likeCount
        val orderRaw = (metric.salesAmount).toDouble()
        val orderScore = if (orderRaw > 0) WEIGHT_ORDER * Math.log10(orderRaw + 1) * metric.orderCount else 0.0
        return viewScore + likeScore + orderScore
    }

    private data class ScoredProduct(
        val productId: Long,
        val score: Double,
        val viewCount: Long,
        val likeCount: Long,
        val orderCount: Long,
        val salesAmount: Long,
    )

    companion object {
        private const val TOP_N = 100
        private const val CHUNK_SIZE = 500
        private const val WEIGHT_VIEW = 0.1
        private const val WEIGHT_LIKE = 0.2
        private const val WEIGHT_ORDER = 0.7
    }
}
