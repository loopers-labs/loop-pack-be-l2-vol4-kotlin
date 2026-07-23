package com.loopers.batch.job.productrank.step

import com.loopers.batch.job.productrank.item.ProductScore
import org.springframework.batch.core.configuration.annotation.JobScope
import org.springframework.stereotype.Component

/**
 * 변형 B2의 인메모리 집계 상태. Job 실행 단위로 살고 끝나면 버려진다(@JobScope).
 * chunk Step은 단일 스레드로 돌므로 동기화가 필요 없다. Job이 중간에 죽으면 상태가 휘발되어
 * 처음부터 재실행해야 한다 — staging 변형과의 트레이드오프로, 실험 설계상 의도된 제약이다.
 */
@JobScope
@Component
class ProductScoreAccumulator {
    private val scores = HashMap<Long, Long>()

    fun accumulate(productId: Long, delta: Long) {
        scores.merge(productId, delta, Long::plus)
    }

    fun size(): Int = scores.size

    /** 점수 내림차순, 동점은 product_id 오름차순(RankConfirmTasklet과 동일한 tie-break). */
    fun top(n: Int): List<ProductScore> =
        scores.entries
            .sortedWith(compareByDescending<Map.Entry<Long, Long>> { it.value }.thenBy { it.key })
            .take(n)
            .map { ProductScore(productId = it.key, score = it.value) }
}
