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
import jakarta.persistence.Version
import java.time.ZonedDateTime

@Entity
@Table(
    name = "user_coupons",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_user_coupons_user_coupon", columnNames = ["user_id", "coupon_id"]),
    ],
)
class UserCouponModel(
    userId: Long,
    couponId: Long,
) : BaseEntity() {
    @Column(name = "user_id", nullable = false)
    var userId: Long = userId
        protected set

    @Column(name = "coupon_id", nullable = false)
    var couponId: Long = couponId
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: UserCouponStatus = UserCouponStatus.AVAILABLE
        protected set

    @Column(name = "used_at")
    var usedAt: ZonedDateTime? = null
        protected set

    // 낙관적 락: 동시 사용 시 한 트랜잭션만 커밋에 성공한다.
    @Version
    @Column(nullable = false)
    var version: Long = 0
        protected set

    init {
        if (userId <= 0) throw CoreException(ErrorType.BAD_REQUEST, "회원 ID는 양수여야 합니다.")
        if (couponId <= 0) throw CoreException(ErrorType.BAD_REQUEST, "쿠폰 ID는 양수여야 합니다.")
    }

    fun currentStatus(coupon: CouponModel, now: ZonedDateTime): UserCouponStatus {
        return when {
            status == UserCouponStatus.USED -> UserCouponStatus.USED
            coupon.isExpired(now) -> UserCouponStatus.EXPIRED
            else -> UserCouponStatus.AVAILABLE
        }
    }

    // 결제 실패 보상: 사용된 쿠폰을 다시 사용 가능 상태로 되돌린다.
    // ponytail: AVAILABLE 재호출은 no-op (보상 중복 안전).
    fun revert() {
        if (status == UserCouponStatus.USED) {
            status = UserCouponStatus.AVAILABLE
            usedAt = null
        }
    }

    fun use(coupon: CouponModel, now: ZonedDateTime) {
        if (coupon.id != couponId) throw CoreException(ErrorType.BAD_REQUEST, "쿠폰 정보가 일치하지 않습니다.")
        when (currentStatus(coupon = coupon, now = now)) {
            UserCouponStatus.USED -> throw CoreException(ErrorType.CONFLICT, "이미 사용된 쿠폰입니다.")
            UserCouponStatus.EXPIRED -> throw CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.")
            UserCouponStatus.AVAILABLE -> {
                status = UserCouponStatus.USED
                usedAt = now
            }
        }
    }
}
