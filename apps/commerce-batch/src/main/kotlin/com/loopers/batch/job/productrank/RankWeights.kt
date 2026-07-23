package com.loopers.batch.job.productrank

/**
 * 랭킹 점수 가중치 (저장 스케일 ×10 — ranking_weight_config와 동일). 일간 Redis 랭킹과 같은
 * 스케일을 쓰므로 기간별 점수를 API에서 섞어 보여줘도 단위가 어긋나지 않는다.
 */
data class RankWeights(
    val viewWeight: Long,
    val likeWeight: Long,
    val orderWeight: Long,
) {
    fun weightFor(type: String): Long = when (type) {
        TYPE_VIEW -> viewWeight
        TYPE_LIKE -> likeWeight
        TYPE_SALES -> orderWeight
        else -> 0L
    }

    fun scoreOf(viewCount: Long, likeCount: Long, salesCount: Long): Long =
        viewCount * viewWeight + likeCount * likeWeight + salesCount * orderWeight

    companion object {
        const val TYPE_VIEW = "VIEW"
        const val TYPE_LIKE = "LIKE"
        const val TYPE_SALES = "SALES"

        /** 논리 1/5/50의 저장 스케일. 활성 가중치 버전이 없을 때의 폴백. */
        val DEFAULT = RankWeights(viewWeight = 10L, likeWeight = 50L, orderWeight = 500L)
    }
}
