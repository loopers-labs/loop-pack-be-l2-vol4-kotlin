package com.loopers.domain.metrics

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.ZonedDateTime

/**
 * 상품별 집계 메트릭. 상품당 1행이며, 각 이벤트를 원자적 upsert(증감) 로 반영한다.
 * 쓰기는 네이티브 upsert 로만 수행하고, 이 엔티티는 조회 용도로 사용한다.
 */
@Entity
@Table(name = "product_metrics")
class ProductMetricsModel(
    @Id
    @Column(name = "product_id")
    val productId: Long = 0,
    @Column(name = "like_count", nullable = false)
    var likeCount: Long = 0,
    @Column(name = "view_count", nullable = false)
    var viewCount: Long = 0,
    @Column(name = "sales_count", nullable = false)
    var salesCount: Long = 0,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: ZonedDateTime = ZonedDateTime.now(),
)
