package com.loopers.batch.job.productrank.item

/** 변형 A(GROUP BY reader)가 읽는 상품별 기간 집계 행. */
data class AggregatedMetricRow(
    val productId: Long,
    val viewCount: Long,
    val likeCount: Long,
    val salesCount: Long,
)

/** 변형 B(raw reader)가 읽는 product_metrics 원시 행. */
data class RawMetricRow(
    val productId: Long,
    val type: String,
    val count: Long,
)

/** 집계 Step의 출력 — 상품별 (부분)점수. A는 최종 점수, B는 행 단위 delta. */
data class ProductScore(
    val productId: Long,
    val score: Long,
)
