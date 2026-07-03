package com.loopers.domain.metrics

interface ProductMetricsRepository {
    /**
     * 상품 메트릭을 원자적으로 upsert(증감) 한다.
     * 서로 다른 파티션의 이벤트가 같은 상품 행을 동시에 갱신해도 손실 없이 누적된다.
     */
    fun upsert(productId: Long, likeDelta: Long, viewDelta: Long, salesDelta: Long)
    fun findByProductId(productId: Long): ProductMetricsModel?
}
