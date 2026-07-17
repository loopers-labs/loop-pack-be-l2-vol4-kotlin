package com.loopers.domain.ranking

import org.springframework.stereotype.Component

/**
 * 랭킹 점수 갱신 서비스.
 * Kafka Consumer가 이벤트를 소비할 때 이 서비스를 통해 Redis ZSET에 점수를 누적한다.
 *
 * 가중치 설계:
 * - view: 0.1 (가장 빈번하므로 낮은 가중치)
 * - like: 0.2 (구매 의향 시그널)
 * - order: 0.7 (실제 구매 결정 = 가장 강한 시그널)
 *
 * Score 계산:
 * - view: weight × 1 = 0.1
 * - like: weight × 1 = 0.2
 * - order: weight × (price × quantity) 정규화 적용
 */
@Component
class RankingService(
    private val rankingRepository: RankingRepository,
) {

    /** 상품 조회 이벤트 → 랭킹 점수 반영 */
    fun recordView(productId: Long) {
        rankingRepository.incrementScore(productId, WEIGHT_VIEW * SCORE_VIEW)
    }

    /** 좋아요 이벤트 → 랭킹 점수 반영 */
    fun recordLike(productId: Long) {
        rankingRepository.incrementScore(productId, WEIGHT_LIKE * SCORE_LIKE)
    }

    /** 좋아요 취소 이벤트 → 랭킹 점수 차감 */
    fun recordUnlike(productId: Long) {
        rankingRepository.incrementScore(productId, -(WEIGHT_LIKE * SCORE_LIKE))
    }

    /**
     * 주문 이벤트 → 랭킹 점수 반영.
     * 주문 금액에 log를 적용하여 고가 상품이 과도하게 유리하지 않도록 정규화한다.
     */
    fun recordOrder(productId: Long, quantity: Long, price: Long) {
        val rawScore = (price * quantity).toDouble()
        val normalizedScore = if (rawScore > 0) Math.log10(rawScore + 1) else 0.0
        rankingRepository.incrementScore(productId, WEIGHT_ORDER * normalizedScore)
    }

    companion object {
        const val WEIGHT_VIEW = 0.1
        const val WEIGHT_LIKE = 0.2
        const val WEIGHT_ORDER = 0.7

        private const val SCORE_VIEW = 1.0
        private const val SCORE_LIKE = 1.0
    }
}
