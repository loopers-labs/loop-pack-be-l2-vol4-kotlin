package com.loopers.domain.coupon

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.ZonedDateTime

@Entity
@Table(
    name = "coupon_publish_inboxes",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_coupon_publish_inboxes_idempotency_key", columnNames = ["idempotency_key"]),
    ],
)
class CouponPublishInbox(
    @Column(name = "idempotency_key", nullable = false, length = 36)
    val idempotencyKey: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    val eventType: CouponPublishEventType,

    @Column(name = "coupon_id", nullable = false)
    val couponId: Long,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "processed_at", nullable = false)
    val processedAt: ZonedDateTime = ZonedDateTime.now(),
) : BaseEntity() {
    init {
        validate()
    }

    override fun guard() {
        validate()
    }

    private fun validate() {
        if (idempotencyKey.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "멱등키는 비어있을 수 없습니다.")
        if (couponId <= 0) throw CoreException(ErrorType.BAD_REQUEST, "쿠폰 ID는 0보다 커야 합니다.")
        if (userId <= 0) throw CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 0보다 커야 합니다.")
    }
}
