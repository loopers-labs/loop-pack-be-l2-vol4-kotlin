package com.loopers.domain.like.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "product_metrics")
class ProductLikeCountJpaEntity(
    @Id
    @Column(name = "product_id", nullable = false)
    var productId: Long,
    @Column(name = "like_count", nullable = false)
    var likeCount: Long = 0,
    @Column(name = "sales_count", nullable = false)
    var salesCount: Long = 0,
    @Column(name = "view_count", nullable = false)
    var viewCount: Long = 0,
    @Column(name = "last_event_at")
    var lastEventAt: ZonedDateTime? = null,
    @Column(name = "last_like_event_at")
    var lastLikeEventAt: ZonedDateTime? = null,
    @Column(name = "last_sales_event_at")
    var lastSalesEventAt: ZonedDateTime? = null,
    @Column(name = "last_view_event_at")
    var lastViewEventAt: ZonedDateTime? = null,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: ZonedDateTime = ZonedDateTime.now(),
) {
    @PrePersist
    @PreUpdate
    fun touch() {
        updatedAt = ZonedDateTime.now()
    }
}
