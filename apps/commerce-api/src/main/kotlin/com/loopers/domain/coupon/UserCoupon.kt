package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.time.LocalDateTime

class UserCoupon(
    val id: Long? = null,
    val userId: Long,
    val couponId: Long,
    usedAt: LocalDateTime? = null,
) {
    var usedAt: LocalDateTime? = usedAt
        private set

    init {
        if (userId <= 0) throw CoreException(ErrorType.BAD_REQUEST, "유효하지 않은 사용자 ID 입니다.")
        if (couponId <= 0) throw CoreException(ErrorType.BAD_REQUEST, "유효하지 않은 쿠폰 ID 입니다.")
    }

    fun isUsed(): Boolean = usedAt != null
}
