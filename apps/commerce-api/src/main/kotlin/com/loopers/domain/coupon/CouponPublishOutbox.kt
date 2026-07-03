package com.loopers.domain.coupon

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Lob
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.ZonedDateTime

@Entity
@Table(
    name = "coupon_publish_outboxes",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_coupon_publish_outboxes_idempotency_key", columnNames = ["idempotency_key"]),
        UniqueConstraint(name = "uk_coupon_publish_outboxes_event_coupon_user", columnNames = ["event_type", "coupon_id", "user_id"]),
    ],
)
class CouponPublishOutbox(
    @Column(name = "idempotency_key", nullable = false, length = 36)
    val idempotencyKey: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    val eventType: CouponPublishEventType,

    @Column(name = "event_id", nullable = false)
    val eventId: Long,

    @Column(name = "coupon_id", nullable = false)
    val couponId: Long,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Lob
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    val payload: String,

    @Column(name = "published_at")
    var publishedAt: ZonedDateTime? = null,
) : BaseEntity() {
    init {
        validate()
    }

    override fun guard() {
        validate()
    }

    fun markPublished(publishedAt: ZonedDateTime = ZonedDateTime.now()) {
        this.publishedAt = publishedAt
    }

    private fun validate() {
        if (idempotencyKey.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "멱등키는 비어있을 수 없습니다.")
        if (eventId <= 0) throw CoreException(ErrorType.BAD_REQUEST, "이벤트 ID는 0보다 커야 합니다.")
        if (couponId <= 0) throw CoreException(ErrorType.BAD_REQUEST, "쿠폰 ID는 0보다 커야 합니다.")
        if (userId <= 0) throw CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 0보다 커야 합니다.")
        if (payload.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "메시지 페이로드는 비어있을 수 없습니다.")
    }
}
