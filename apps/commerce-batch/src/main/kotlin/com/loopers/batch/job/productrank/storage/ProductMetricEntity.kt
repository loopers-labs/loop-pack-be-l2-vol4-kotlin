package com.loopers.batch.job.productrank.storage

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.io.Serializable
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * commerce-collector 소유 테이블의 읽기 전용 미러 매핑. 배치는 JDBC로 직접 읽지만,
 * 로컬(ddl-auto: update)/테스트(create) 컨텍스트에서 스키마를 만들 수 있도록 정의를 함께 둔다.
 * 스키마 변경 시 collector의 ProductMetricEntity와 동기화할 것.
 */
@Entity
@Table(
    name = "product_metrics",
    indexes = [
        Index(name = "idx_product_metrics_type_count", columnList = "type, count"),
        Index(name = "idx_product_metrics_metric_date", columnList = "metric_date"),
    ],
)
class ProductMetricEntity(
    @EmbeddedId
    val id: ProductMetricId,

    @Column(name = "count", nullable = false)
    var count: Long = 0L,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: ZonedDateTime,
)

@Embeddable
class ProductMetricId(
    @Column(name = "product_id") val productId: Long = 0L,
    @Column(name = "type") val type: String = "",
    @Column(name = "metric_date") val metricDate: LocalDate = LocalDate.EPOCH,
) : Serializable {
    override fun equals(other: Any?): Boolean =
        other is ProductMetricId && productId == other.productId && type == other.type && metricDate == other.metricDate

    override fun hashCode(): Int = (31 * productId.hashCode() + type.hashCode()) * 31 + metricDate.hashCode()
}
