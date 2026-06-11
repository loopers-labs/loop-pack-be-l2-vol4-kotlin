package com.loopers.coupon.domain

import com.loopers.domain.BaseEntity
import com.loopers.shared.domain.Money
import com.loopers.support.error.BadRequestException
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.time.LocalDateTime

@Entity
class Coupon(
    @Column(name = "type", nullable = false, updatable = false)
    @Enumerated(EnumType.STRING)
    val type: CouponType,
    @Column(name = "name", nullable = false, updatable = false)
    val name: String,
    // H2 2.x 에서 VALUE 는 예약어라 컬럼명만 discount_value 로 둔다 (프로퍼티명은 value 유지)
    @Column(name = "discount_value", nullable = false, updatable = false)
    val value: Long,
    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "minOrderAmount", nullable = false, updatable = false))
    val minOrderAmount: Money,
    @Column(name = "expiredAt", nullable = false, updatable = false)
    val expiredAt: LocalDateTime,
    @Column(name = "createdBy", nullable = false, updatable = false)
    val createdBy: Long,
) : BaseEntity() {
    fun isExpired(now: LocalDateTime): Boolean = this.expiredAt < now

    /**
     * 이 쿠폰을 [orderAmount] 주문에 사용할 자격이 있는지 검증한다. (할인 계산은 [DiscountPolicy] 가 담당)
     */
    fun validateUsable(orderAmount: Money, now: LocalDateTime) {
        if (orderAmount.amount < minOrderAmount.amount) {
            throw BadRequestException(CouponErrorCode.MIN_ORDER_NOT_MET)
        }
        if (isExpired(now)) {
            throw BadRequestException(CouponErrorCode.EXPIRED)
        }
    }
}
