package com.loopers.projection.ranking.application

data class RankingRdbSyncProperties(
    val enabled: Boolean,
    val fixedDelayMs: Long,
    val topN: Int,
) {
    init {
        require(fixedDelayMs > 0) { "fixed-delay-ms 는 양수여야 합니다." }
        require(topN > 0) { "top-n 은 양수여야 합니다." }
    }
}
