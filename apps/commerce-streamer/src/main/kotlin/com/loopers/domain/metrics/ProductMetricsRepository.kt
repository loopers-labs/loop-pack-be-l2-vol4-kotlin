package com.loopers.domain.metrics

/**
 * 상품 지표 저장소 인터페이스.
 */
interface ProductMetricsRepository {
    /** 상품 ID로 지표를 조회한다. 없으면 null. */
    fun findByProductId(productId: Long): ProductMetricsModel?

    /** 지표를 저장(생성 또는 갱신)한다. */
    fun save(model: ProductMetricsModel): ProductMetricsModel
}
