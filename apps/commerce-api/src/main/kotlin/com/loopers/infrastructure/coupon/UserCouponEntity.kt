package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.UserCoupon
import com.loopers.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime

@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(
    name = "user_coupon",
    indexes = [
        Index(name = "idx_user_coupon_user_id", columnList = "user_id"),
        Index(name = "idx_user_coupon_template_id", columnList = "coupon_template_id"),
    ],
)
class UserCouponEntity(
    @Column(name = "coupon_template_id", nullable = false)
    val couponTemplateId: Long,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: PersistedCouponStatus,

    @Column(name = "issued_at", nullable = false)
    val issuedAt: LocalDateTime,

    @Column(name = "used_at")
    var usedAt: LocalDateTime?,
) : BaseEntity() {
    /**
     * 낙관적 락 버전. 동일 발급 쿠폰을 동시에 사용(USED 전이)하려는 경합에서
     * 커밋(flush) 시점에 version 을 비교해 1명만 성공시키고 나머지는 충돌(OptimisticLockingFailureException)로 실패시킨다.
     * 도메인 모델([UserCoupon])에는 노출하지 않고 JPA 가 자동 관리한다.
     */
    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0L
        protected set

    fun toDomain(): UserCoupon = UserCoupon(
        id = id,
        couponTemplateId = couponTemplateId,
        userId = userId,
        status = status.toDomain(),
        issuedAt = issuedAt,
        usedAt = usedAt,
    )

    companion object {
        fun from(domain: UserCoupon): UserCouponEntity = UserCouponEntity(
            couponTemplateId = domain.couponTemplateId,
            userId = domain.userId,
            status = PersistedCouponStatus.from(domain.status),
            issuedAt = domain.issuedAt,
            usedAt = domain.usedAt,
        )
    }
}
