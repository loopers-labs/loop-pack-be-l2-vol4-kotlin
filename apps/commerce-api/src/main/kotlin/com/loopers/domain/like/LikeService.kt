package com.loopers.domain.like

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

@Transactional
@Component
class LikeService(
    private val likeRepository: LikeRepository,
    private val productLikeCountRepository: ProductLikeCountRepository,
) {
    fun getLikeCount(productId: Long): Int {
        return likeRepository.findAllByProductId(productId).count { it.available() }
    }

    fun getLikeCountGroupByProductId(productModelIds: List<Long>): Map<ProductId, LikeCount> {
        return likeRepository.findAllByProductIdIn(productModelIds)
            .filter { it.available() }
            .groupingBy { ProductId(it.productId) }
            .eachCount()
            .mapValues { LikeCount(it.value) }
    }

    /**
     * 집계 테이블(product_like_count) 기반 좋아요 수 조회.
     * like 원본을 통째로 로드하는 [getLikeCountGroupByProductId] 의 병목(인기상품 수만 row 로드)을 피한다.
     * 트레이드오프: 배치 갱신이라 약간 stale (ADR-0002 D0 — LikeCount 는 캐시 허용 정책).
     * 집계 row 가 없는 신규 상품은 결과에서 빠짐 → 호출부에서 0 처리.
     */
    @Transactional(readOnly = true)
    fun getLikeCountFromAggregation(productIds: List<Long>): Map<ProductId, LikeCount> {
        return productLikeCountRepository.findByProductIdIn(productIds)
            .associate { ProductId(it.productId) to LikeCount(it.likeCount) }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun applyLikeCount(event: LikeEvent.Changed) {
        val delta = when (event.type) {
            LikeEvent.LikeChangeType.LIKED -> 1
            LikeEvent.LikeChangeType.UNLIKED -> -1
        }
        productLikeCountRepository.upsertLikeCount(event.productId, delta, ZonedDateTime.now())
    }

    fun addLike(userId: Long, productId: Long): LikeResult =
        likeRepository.like(userId, productId)

    fun remove(userId: Long, productId: Long): Boolean {
        val likeModel = likeRepository.findByUserIdAndProductId(userId, productId) ?: return false
        if (!likeModel.available()) return false
        likeModel.delete()
        return true
    }

    fun getLikeByUserId(userId: Long): List<LikeModel> {
        return likeRepository.
        findAllByUserId(userId)
    }
}
