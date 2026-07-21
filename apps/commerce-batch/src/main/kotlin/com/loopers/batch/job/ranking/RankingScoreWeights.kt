package com.loopers.batch.job.ranking

object RankingScoreWeights {
    const val VIEW = 0.1
    const val LIKE = 0.2
    const val SALES = 0.7

    fun score(likeCount: Int, salesCount: Int, viewCount: Int): Double =
        viewCount * VIEW + likeCount * LIKE + salesCount * SALES
}
