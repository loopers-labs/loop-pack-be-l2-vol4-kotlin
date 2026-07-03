package com.loopers.domain.like.application

import com.loopers.domain.like.port.LikeBulkRepository
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import kotlin.system.measureTimeMillis

/**
 * 읽기 최적화(인덱스/캐시) 실습용 좋아요 분포 데이터를 생성·적재하는 오케스트레이터.
 * likes 를 분포 적재한 뒤 product_metrics 를 집계 파생해 두 테이블 정합성을 맞춘다.
 * 로컬 전용이며 [com.loopers.domain.like.presentation.LikeSeedController] 를 통해 트리거된다.
 */
@Component
@Profile("local")
class LikeSeeder(
    private val likeBulkRepository: LikeBulkRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun seedLikes(maxLikesPerProduct: Int): SeedResult {
        val existingLikes = likeBulkRepository.countLikes()
        val existingCounts = likeBulkRepository.countLikeCounts()
        if (existingLikes > 0L || existingCounts > 0L) {
            log.warn("likes={}, product_metrics={} 이미 존재합니다. 시딩을 건너뜁니다.", existingLikes, existingCounts)
            return SeedResult(likesInserted = 0, countsInserted = 0, elapsedMillis = 0, skipped = true)
        }

        var likesInserted = 0
        var countsInserted = 0
        val elapsed = measureTimeMillis {
            likesInserted = likeBulkRepository.seedLikesByDistribution(maxLikesPerProduct)
            countsInserted = likeBulkRepository.deriveLikeCounts()
        }
        log.info("좋아요 시딩 완료: likes={}, counts={}, {}ms", likesInserted, countsInserted, elapsed)
        return SeedResult(likesInserted, countsInserted, elapsed, skipped = false)
    }
}
