package com.loopers.infrastructure.metric

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable
import java.time.LocalDate

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
